package co.com.nequi.model.order.gateways;

import co.com.nequi.model.order.Order;
import reactor.core.publisher.Mono;

public interface OrderRepository {

    /**
     * reason: nullable, populated only when order.getOrderStatus() == REJECTED,
     * describing which ticket(s) failed and why. Kept out of the shared Order
     * record so its shape stays identical across ticket-reservation-service,
     * ticket-purchase-service and ticket-availability-service.
     */
    Mono<Order> save(Order order, String reason);

    Mono<Order> findLatestByOrderId(String orderId);
}
