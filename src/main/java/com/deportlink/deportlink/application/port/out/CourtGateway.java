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

    record CourtSnapshot(
            Long id,
            double pricePerHour,
            String name,
            String sportName,
            String branchName,
            String branchAddress
    ) {}
}