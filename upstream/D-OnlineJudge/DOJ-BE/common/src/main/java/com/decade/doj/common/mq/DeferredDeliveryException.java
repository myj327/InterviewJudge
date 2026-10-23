package com.decade.doj.common.mq;

// Another delivery owns the lease; no execution attempt has happened on this delivery.
public class DeferredDeliveryException extends RuntimeException {
    public DeferredDeliveryException(String message) { super(message); }
}
