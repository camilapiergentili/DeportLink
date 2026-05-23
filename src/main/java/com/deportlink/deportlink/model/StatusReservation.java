package com.deportlink.deportlink.model;

public enum StatusReservation {

    RESERVADO(true),
    FINALIZADO(false),
    CANCELADO(false),
    REPROGRAMADO(false);

    private final boolean occupiesSlot;

    StatusReservation(boolean occupiesSlot) {
        this.occupiesSlot = occupiesSlot;
    }

    public boolean occupiesSlot() {
        return occupiesSlot;
    }
}