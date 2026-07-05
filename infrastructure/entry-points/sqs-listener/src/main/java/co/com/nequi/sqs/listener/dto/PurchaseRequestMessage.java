package co.com.nequi.sqs.listener.dto;

import java.util.List;

/**
 * Mirrors the exact shape published by ticket-reservation-service's
 * PurchaseRequestSQSAdapter to the purchase-requests queue: orderId, eventId,
 * ticketIds, userId, requestedAt (ISO-8601 string). Field names and types must
 * stay in lockstep with that producer.
 */
public record PurchaseRequestMessage(
        String orderId,
        String eventId,
        List<String> ticketIds,
        String userId,
        String requestedAt
) {
}
