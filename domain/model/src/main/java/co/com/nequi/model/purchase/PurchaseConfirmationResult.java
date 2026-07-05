package co.com.nequi.model.purchase;

import co.com.nequi.model.order.Order;
import co.com.nequi.model.order.OrderStatus;

import java.util.List;

public sealed interface PurchaseConfirmationResult {

    record Confirmed(Order order) implements PurchaseConfirmationResult {
    }

    record Rejected(Order order, List<String> rejectedTicketIds) implements PurchaseConfirmationResult {
    }

    record AlreadyProcessed(String orderId, OrderStatus orderStatus) implements PurchaseConfirmationResult {
    }
}
