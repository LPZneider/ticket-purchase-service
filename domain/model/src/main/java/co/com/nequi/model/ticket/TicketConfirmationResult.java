package co.com.nequi.model.ticket;

import java.util.List;
import java.util.stream.Collectors;

public sealed interface TicketConfirmationResult {

    record Confirmed(Ticket ticket) implements TicketConfirmationResult {
    }

    record Rejected(String ticketId, String reason) implements TicketConfirmationResult {
    }

    static List<String> rejectedTicketIds(List<TicketConfirmationResult> results) {
        return results.stream()
                .filter(Rejected.class::isInstance)
                .map(result -> ((Rejected) result).ticketId())
                .toList();
    }

    static String rejectionReason(List<TicketConfirmationResult> results) {
        return results.stream()
                .filter(Rejected.class::isInstance)
                .map(Rejected.class::cast)
                .map(rejected -> rejected.ticketId() + ": " + rejected.reason())
                .collect(Collectors.joining("; "));
    }
}
