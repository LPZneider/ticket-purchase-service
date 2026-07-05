package co.com.nequi.sqs.listener;

import co.com.nequi.model.order.Order;
import co.com.nequi.model.order.OrderStatus;
import co.com.nequi.model.purchase.PurchaseConfirmationResult;
import co.com.nequi.usecase.purchase.ConfirmPurchaseUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SQSProcessorTest {

    @Mock
    private ConfirmPurchaseUseCase confirmPurchaseUseCase;

    private SQSProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SQSProcessor(confirmPurchaseUseCase, new ObjectMapper());
    }

    private static Message message(String body) {
        return Message.builder().body(body).build();
    }

    private static final String VALID_BODY = """
            {"orderId":"order-1","eventId":"event-1","ticketIds":["t1","t2"],"userId":"user-1","requestedAt":"2026-07-01T10:00:00Z"}
            """;

    @Test
    void shouldCompleteWhenOrderIsConfirmed() {
        Order order = Order.builder().orderId("order-1").eventId("event-1").ticketIds(List.of("t1", "t2"))
                .userId("user-1").orderStatus(OrderStatus.CONFIRMED).createdAt(Instant.now()).build();
        when(confirmPurchaseUseCase.confirm(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(Mono.just(new PurchaseConfirmationResult.Confirmed(order)));

        StepVerifier.create(processor.apply(message(VALID_BODY)))
                .verifyComplete();
    }

    @Test
    void shouldCompleteWhenOrderIsRejected() {
        Order order = Order.builder().orderId("order-1").eventId("event-1").ticketIds(List.of("t1", "t2"))
                .userId("user-1").orderStatus(OrderStatus.REJECTED).createdAt(Instant.now()).build();
        when(confirmPurchaseUseCase.confirm(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(Mono.just(new PurchaseConfirmationResult.Rejected(order, List.of("t2"))));

        StepVerifier.create(processor.apply(message(VALID_BODY)))
                .verifyComplete();
    }

    @Test
    void shouldCompleteWhenOrderWasAlreadyProcessed() {
        when(confirmPurchaseUseCase.confirm(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(Mono.just(new PurchaseConfirmationResult.AlreadyProcessed("order-1", OrderStatus.CONFIRMED)));

        StepVerifier.create(processor.apply(message(VALID_BODY)))
                .verifyComplete();
    }

    @Test
    void shouldPropagateErrorWhenUseCaseFailsTransiently() {
        when(confirmPurchaseUseCase.confirm(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("DynamoDB throttled")));

        StepVerifier.create(processor.apply(message(VALID_BODY)))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldPropagateErrorForMalformedMessageBody() {
        StepVerifier.create(processor.apply(message("not-json")))
                .expectError()
                .verify();
    }
}
