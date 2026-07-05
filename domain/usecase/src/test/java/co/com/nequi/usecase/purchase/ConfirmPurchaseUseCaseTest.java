package co.com.nequi.usecase.purchase;

import co.com.nequi.model.order.Order;
import co.com.nequi.model.order.OrderStatus;
import co.com.nequi.model.order.gateways.OrderRepository;
import co.com.nequi.model.purchase.PurchaseConfirmationResult;
import co.com.nequi.model.ticket.Ticket;
import co.com.nequi.model.ticket.TicketConfirmationResult;
import co.com.nequi.model.ticket.TicketStatus;
import co.com.nequi.model.ticket.gateways.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmPurchaseUseCaseTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private OrderRepository orderRepository;

    private ConfirmPurchaseUseCase useCase;

    private static final String ORDER_ID = "order-1";
    private static final String EVENT_ID = "event-1";
    private static final List<String> TICKET_IDS = List.of("t1", "t2");
    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        useCase = new ConfirmPurchaseUseCase(ticketRepository, orderRepository);
    }

    @Test
    void shouldConfirmOrderWhenAllTicketsAreMarkedAsSold() {
        when(orderRepository.findLatestByOrderId(ORDER_ID)).thenReturn(Mono.empty());
        when(ticketRepository.conditionalMarkAsSold(eq(EVENT_ID), anyString(), eq(ORDER_ID)))
                .thenAnswer(invocation -> Mono.just(new TicketConfirmationResult.Confirmed(
                        Ticket.builder().ticketId(invocation.getArgument(1)).eventId(EVENT_ID)
                                .status(TicketStatus.SOLD).orderId(ORDER_ID).build())));
        when(orderRepository.save(any(Order.class), any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.confirm(ORDER_ID, EVENT_ID, TICKET_IDS, USER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(PurchaseConfirmationResult.Confirmed.class);
                    Order order = ((PurchaseConfirmationResult.Confirmed) result).order();
                    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
                    assertThat(order.getOrderId()).isEqualTo(ORDER_ID);
                })
                .verifyComplete();

        verify(orderRepository).save(any(Order.class), isNull());
    }

    @Test
    void shouldRejectOrderWhenAnyTicketFailsCondition() {
        when(orderRepository.findLatestByOrderId(ORDER_ID)).thenReturn(Mono.empty());
        when(ticketRepository.conditionalMarkAsSold(EVENT_ID, "t1", ORDER_ID))
                .thenReturn(Mono.just(new TicketConfirmationResult.Confirmed(
                        Ticket.builder().ticketId("t1").eventId(EVENT_ID).status(TicketStatus.SOLD).orderId(ORDER_ID).build())));
        when(ticketRepository.conditionalMarkAsSold(EVENT_ID, "t2", ORDER_ID))
                .thenReturn(Mono.just(new TicketConfirmationResult.Rejected("t2", "condition check failed")));
        when(orderRepository.save(any(Order.class), any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.confirm(ORDER_ID, EVENT_ID, TICKET_IDS, USER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(PurchaseConfirmationResult.Rejected.class);
                    PurchaseConfirmationResult.Rejected rejected = (PurchaseConfirmationResult.Rejected) result;
                    assertThat(rejected.order().getOrderStatus()).isEqualTo(OrderStatus.REJECTED);
                    assertThat(rejected.rejectedTicketIds()).containsExactly("t2");
                })
                .verifyComplete();

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderRepository).save(any(Order.class), reasonCaptor.capture());
        assertThat(reasonCaptor.getValue()).contains("t2").contains("condition check failed");
    }

    @Test
    void shouldReturnAlreadyProcessedAndSkipTicketUpdatesWhenOrderIsConfirmed() {
        Order existing = Order.builder().orderId(ORDER_ID).eventId(EVENT_ID).ticketIds(TICKET_IDS)
                .userId(USER_ID).orderStatus(OrderStatus.CONFIRMED).createdAt(Instant.now()).build();
        when(orderRepository.findLatestByOrderId(ORDER_ID)).thenReturn(Mono.just(existing));

        StepVerifier.create(useCase.confirm(ORDER_ID, EVENT_ID, TICKET_IDS, USER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(PurchaseConfirmationResult.AlreadyProcessed.class);
                    assertThat(((PurchaseConfirmationResult.AlreadyProcessed) result).orderStatus())
                            .isEqualTo(OrderStatus.CONFIRMED);
                })
                .verifyComplete();

        verify(ticketRepository, never()).conditionalMarkAsSold(any(), any(), any());
        verify(orderRepository, never()).save(any(), any());
    }

    @Test
    void shouldReturnAlreadyProcessedWhenOrderIsRejected() {
        Order existing = Order.builder().orderId(ORDER_ID).eventId(EVENT_ID).ticketIds(TICKET_IDS)
                .userId(USER_ID).orderStatus(OrderStatus.REJECTED).createdAt(Instant.now()).build();
        when(orderRepository.findLatestByOrderId(ORDER_ID)).thenReturn(Mono.just(existing));

        StepVerifier.create(useCase.confirm(ORDER_ID, EVENT_ID, TICKET_IDS, USER_ID))
                .assertNext(result -> assertThat(result).isInstanceOf(PurchaseConfirmationResult.AlreadyProcessed.class))
                .verifyComplete();

        verify(ticketRepository, never()).conditionalMarkAsSold(anyString(), anyString(), anyString());
    }

    @Test
    void shouldReturnAlreadyProcessedWhenOrderIsExpired() {
        Order existing = Order.builder().orderId(ORDER_ID).eventId(EVENT_ID).ticketIds(TICKET_IDS)
                .userId(USER_ID).orderStatus(OrderStatus.EXPIRED).createdAt(Instant.now()).build();
        when(orderRepository.findLatestByOrderId(ORDER_ID)).thenReturn(Mono.just(existing));

        StepVerifier.create(useCase.confirm(ORDER_ID, EVENT_ID, TICKET_IDS, USER_ID))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(PurchaseConfirmationResult.AlreadyProcessed.class);
                    assertThat(((PurchaseConfirmationResult.AlreadyProcessed) result).orderStatus())
                            .isEqualTo(OrderStatus.EXPIRED);
                })
                .verifyComplete();

        verify(ticketRepository, never()).conditionalMarkAsSold(anyString(), anyString(), anyString());
        verify(orderRepository, never()).save(any(), any());
    }

    @Test
    void shouldReprocessWhenExistingOrderIsStillPendingConfirmation() {
        Order existing = Order.builder().orderId(ORDER_ID).eventId(EVENT_ID).ticketIds(TICKET_IDS)
                .userId(USER_ID).orderStatus(OrderStatus.PENDING_CONFIRMATION).createdAt(Instant.now()).build();
        when(orderRepository.findLatestByOrderId(ORDER_ID)).thenReturn(Mono.just(existing));
        when(ticketRepository.conditionalMarkAsSold(eq(EVENT_ID), anyString(), eq(ORDER_ID)))
                .thenAnswer(invocation -> Mono.just(new TicketConfirmationResult.Confirmed(
                        Ticket.builder().ticketId(invocation.getArgument(1)).eventId(EVENT_ID)
                                .status(TicketStatus.SOLD).orderId(ORDER_ID).build())));
        when(orderRepository.save(any(Order.class), any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.confirm(ORDER_ID, EVENT_ID, TICKET_IDS, USER_ID))
                .assertNext(result -> assertThat(result).isInstanceOf(PurchaseConfirmationResult.Confirmed.class))
                .verifyComplete();
    }

    @Test
    void shouldPropagateTransientErrorWithoutConvertingToRejected() {
        when(orderRepository.findLatestByOrderId(ORDER_ID)).thenReturn(Mono.empty());
        when(ticketRepository.conditionalMarkAsSold(any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("DynamoDB throttled")));

        StepVerifier.create(useCase.confirm(ORDER_ID, EVENT_ID, TICKET_IDS, USER_ID))
                .expectError(RuntimeException.class)
                .verify();

        verify(orderRepository, never()).save(any(), any());
    }
}
