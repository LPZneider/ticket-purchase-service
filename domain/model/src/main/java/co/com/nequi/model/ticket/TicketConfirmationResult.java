package co.com.nequi.model.ticket;

public sealed interface TicketConfirmationResult {

    record Confirmed(Ticket ticket) implements TicketConfirmationResult {
    }

    record Rejected(String ticketId, String reason) implements TicketConfirmationResult {
    }
}
