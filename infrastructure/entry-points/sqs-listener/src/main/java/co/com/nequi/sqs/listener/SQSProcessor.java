package co.com.nequi.sqs.listener;

import co.com.nequi.model.purchase.PurchaseConfirmationResult;
import co.com.nequi.sqs.listener.dto.PurchaseRequestMessage;
import co.com.nequi.usecase.purchase.ConfirmPurchaseUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.function.Function;

/**
 * Maps every business outcome (Confirmed, Rejected, AlreadyProcessed) to a Mono
 * that completes successfully, so the generic SQSListener deletes the message —
 * a business rejection is a resolved outcome, not a processing failure. Only a
 * genuine technical error (thrown by the gateways, or a malformed message body)
 * is left to propagate as Mono.error, so the message is NOT deleted and SQS
 * redelivers it per its visibility timeout, until the DLQ captures it.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class SQSProcessor implements Function<Message, Mono<Void>> {

    private final ConfirmPurchaseUseCase confirmPurchaseUseCase;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> apply(Message message) {
        return Mono.fromCallable(() -> objectMapper.readValue(message.body(), PurchaseRequestMessage.class))
                .flatMap(this::process)
                .then();
    }

    private Mono<PurchaseConfirmationResult> process(PurchaseRequestMessage body) {
        return confirmPurchaseUseCase.confirm(body.orderId(), body.eventId(), body.ticketIds(), body.userId())
                .doOnNext(result -> logResult(body.orderId(), result));
    }

    private void logResult(String orderId, PurchaseConfirmationResult result) {
        switch (result) {
            case PurchaseConfirmationResult.Confirmed confirmed ->
                    log.info("[PURCHASE] Order confirmed | orderId={}", confirmed.order().getOrderId());
            case PurchaseConfirmationResult.Rejected rejected ->
                    log.info("[PURCHASE] Order rejected | orderId={}, rejectedTicketIds={}",
                            rejected.order().getOrderId(), rejected.rejectedTicketIds());
            case PurchaseConfirmationResult.AlreadyProcessed alreadyProcessed ->
                    log.info("[PURCHASE] Order already processed, skipping | orderId={}, status={}",
                            orderId, alreadyProcessed.orderStatus());
        }
    }
}
