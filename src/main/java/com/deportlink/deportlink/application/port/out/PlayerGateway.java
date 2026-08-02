package com.deportlink.deportlink.application.port.out;

import java.util.Optional;

/**
 * Gateway hacia el dominio Player, definido por el caso de uso de reservas.
 * Solo expone nombre y apellido — suficiente para emitir el Ticket.
 */
public interface PlayerGateway {

    Optional<PlayerSnapshot> findById(Long id);

    record PlayerSnapshot(Long id, String firstName, String lastName) {}
}