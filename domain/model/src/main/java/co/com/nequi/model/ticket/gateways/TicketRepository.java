package co.com.nequi.model.ticket.gateways;

import co.com.nequi.model.ticket.TicketConfirmationResult;
import reactor.core.publisher.Mono;

public interface TicketRepository {

    Mono<TicketConfirmationResult> conditionalMarkAsSold(String eventId, String ticketId, String orderId);
}
