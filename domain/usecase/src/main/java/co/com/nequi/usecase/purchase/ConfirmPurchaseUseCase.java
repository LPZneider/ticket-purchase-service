package co.com.nequi.usecase.purchase;

import co.com.nequi.model.order.Order;
import co.com.nequi.model.order.OrderStatus;
import co.com.nequi.model.order.gateways.OrderRepository;
import co.com.nequi.model.purchase.PurchaseConfirmationResult;
import co.com.nequi.model.ticket.TicketConfirmationResult;
import co.com.nequi.model.ticket.gateways.TicketRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ConfirmPurchaseUseCase {

    private final TicketRepository ticketRepository;
    private final OrderRepository orderRepository;

    public Mono<PurchaseConfirmationResult> confirm(String orderId, String eventId, List<String> ticketIds, String userId) {
        return orderRepository.findLatestByOrderId(orderId)
                .filter(ConfirmPurchaseUseCase::isAlreadyResolved)
                .map(order -> (PurchaseConfirmationResult) new PurchaseConfirmationResult.AlreadyProcessed(orderId, order.getOrderStatus()))
                .switchIfEmpty(Mono.defer(() -> processConfirmation(orderId, eventId, ticketIds, userId)));
    }

    private static boolean isAlreadyResolved(Order order) {
        return order.getOrderStatus() == OrderStatus.CONFIRMED
                || order.getOrderStatus() == OrderStatus.REJECTED
                || order.getOrderStatus() == OrderStatus.EXPIRED;
    }

    private Mono<PurchaseConfirmationResult> processConfirmation(String orderId, String eventId, List<String> ticketIds, String userId) {
        return Flux.fromIterable(ticketIds)
                .flatMap(ticketId -> ticketRepository.conditionalMarkAsSold(eventId, ticketId, orderId))
                .collectList()
                .flatMap(results -> saveOrderTransition(orderId, eventId, ticketIds, userId, results));
    }

    private static List<String> rejectedTicketIds(List<TicketConfirmationResult> results) {
        return results.stream()
                .filter(TicketConfirmationResult.Rejected.class::isInstance)
                .map(result -> ((TicketConfirmationResult.Rejected) result).ticketId())
                .toList();
    }

    private static String rejectionReason(List<TicketConfirmationResult> results) {
        return results.stream()
                .filter(TicketConfirmationResult.Rejected.class::isInstance)
                .map(TicketConfirmationResult.Rejected.class::cast)
                .map(rejected -> rejected.ticketId() + ": " + rejected.reason())
                .collect(Collectors.joining("; "));
    }

    private Mono<PurchaseConfirmationResult> saveOrderTransition(String orderId, String eventId, List<String> ticketIds,
                                                                   String userId, List<TicketConfirmationResult> results) {
        List<String> rejectedTicketIds = rejectedTicketIds(results);
        boolean rejected = !rejectedTicketIds.isEmpty();
        OrderStatus status = rejected ? OrderStatus.REJECTED : OrderStatus.CONFIRMED;
        String reason = rejected ? rejectionReason(results) : null;
        Order order = Order.builder()
                .orderId(orderId)
                .eventId(eventId)
                .ticketIds(ticketIds)
                .userId(userId)
                .orderStatus(status)
                .createdAt(Instant.now())
                .build();

        return orderRepository.save(order, reason)
                .map(savedOrder -> rejected
                        ? (PurchaseConfirmationResult) new PurchaseConfirmationResult.Rejected(savedOrder, rejectedTicketIds)
                        : new PurchaseConfirmationResult.Confirmed(savedOrder));
    }
}
