package com.deportlink.deportlink.application.port.out;

import java.util.Optional;

/**
 * Gateway hacia el dominio Court, definido por quién lo necesita: el caso de uso de reservas.
 * Solo expone lo que Reservation necesita — no el CourtEntity completo con todas sus relaciones JPA.
 * <p>
 * Principio ISP: el cliente define la interfaz que necesita, no el proveedor.
 */
public interface CourtGateway {

    Optional<CourtSnapshot> findById(Long id);

    /**
     * Adquiere un lock pesimista sobre la cancha antes de verificar disponibilidad.
     * Protege contra phantom reads en reservas concurrentes para el mismo slot.
     */
    Optional<CourtSnapshot> findByIdForUpdate(Long id);

    /**
     * Adquiere el lock pesimista sobre la cancha asociada a una reserva, derivando el courtId
     * vía JOIN — sin necesidad de conocerlo de antemano ni de leer la reserva primero.
     * <p>
     * Debe ser la PRIMERA lectura de la transacción en el caso de uso que la llama (ver
     * RescheduleReservationUseCase). Si en cambio se leyera la reserva con un SELECT plano
     * antes de este lock, el snapshot de REPEATABLE READ de MySQL quedaría fijado antes de
     * adquirirlo, y una verificación de disponibilidad posterior podría seguir viendo datos
     * anteriores al commit de otra transacción concurrente — confirmado reproducible al 100%
     * con RescheduleReservationConcurrencyTest antes de este método.
     */
    Optional<Long> findCourtIdByReservationForUpdate(Long reservationId);

    record CourtSnapshot(
            Long id,
            double pricePerHour,
            String name,
            String sportName,
            String branchName,
            String branchAddress
    ) {}
}