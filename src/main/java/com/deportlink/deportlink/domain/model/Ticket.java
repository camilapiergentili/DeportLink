package com.deportlink.deportlink.domain.model;

import java.time.LocalDateTime;

/**
 * Value Object: snapshot inmutable del momento de la reserva.
 * Un Ticket captura quién reservó, en qué cancha, cuánto pagó — en el instante de la reserva.
 * Si la cancha cambia de nombre después, el ticket conserva el nombre original.
 * Por eso es un snapshot, no una referencia.
 * <p>
 * Pertenece al agregado Reservation: no existe un Ticket sin una Reservation.
 */
public record Ticket(
        Long id,
        String playerName,
        String playerLastName,
        String courtName,
        String sport,
        String branchName,
        String branchAddress,
        double totalPrice,
        LocalDateTime issuedAt
) {

    /**
     * Factory method: emite un ticket nuevo (sin id, sin fecha de persistencia aún).
     * El id lo asigna la capa de infraestructura tras persistir.
     */
    public static Ticket issue(
            String playerName,
            String playerLastName,
            String courtName,
            String sport,
            String branchName,
            String branchAddress,
            double totalPrice
    ) {
        return new Ticket(
                null, playerName, playerLastName,
                courtName, sport, branchName, branchAddress,
                totalPrice, LocalDateTime.now()
        );
    }

    public Ticket withId(Long id) {
        return new Ticket(id, playerName, playerLastName, courtName, sport,
                branchName, branchAddress, totalPrice, issuedAt);
    }
}