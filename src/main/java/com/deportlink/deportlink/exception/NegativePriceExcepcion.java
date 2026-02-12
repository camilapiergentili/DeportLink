package com.deportlink.deportlink.exception;

public class NegativePriceExcepcion extends RuntimeException {
    public NegativePriceExcepcion(String message) {
        super(message);
    }
}
