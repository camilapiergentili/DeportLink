package com.deportlink.deportlink.application.port.out;

import java.util.Optional;

/**
 * Deriva y bloquea la Court asociada a un ClassSlot en una sola operación — análoga a
 * {@code CourtGateway.findCourtIdByReservationForUpdate}. Se define como gateway NUEVO en vez de
 * agregar un método a {@code CourtGateway} porque ese puerto ya tiene un adapter de producción
 * ({@code CourtGatewayAdapter}); agregarle un método ahora obligaría a implementarlo en esta
 * etapa (solo dominio/casos de uso/puertos) — ver restricción explícita de la Etapa 1B.
 * <p>
 * Debe ser la PRIMERA lectura de la transacción en CreateClassSessionUseCase, antes incluso de
 * cargar el propio ClassSlot: Court es el recurso realmente disputado entre una ClassSession y
 * una Reservation (ambas compiten por el mismo court_id/día/hora — ver CourtOccupancyPort). Si
 * el ClassSlot se leyera primero, el snapshot de REPEATABLE READ de MySQL fijaría el estado de
 * la ocupación de la cancha antes de tomar el lock — mismo bug de fondo que
 * RescheduleReservationConcurrencyTest detectó para CourtGateway.findByIdForUpdate, documentado
 * en el Javadoc de {@code CourtGateway.findCourtIdByReservationForUpdate}.
 */
public interface ClassSlotCourtGateway {

    Optional<Long> findCourtIdByClassSlotForUpdate(Long classSlotId);
}
