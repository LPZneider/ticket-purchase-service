package co.com.nequi.model.order;

public enum OrderStatus {
    PENDING_CONFIRMATION,
    CONFIRMED,
    REJECTED,
    EXPIRED;

    public boolean isFinal() {
        return this == CONFIRMED || this == REJECTED || this == EXPIRED;
    }
}
