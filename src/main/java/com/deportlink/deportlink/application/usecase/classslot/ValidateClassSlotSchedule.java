package com.deportlink.deportlink.application.usecase.classslot;

import com.deportlink.deportlink.application.port.out.ScheduleGateway;
import com.deportlink.deportlink.application.port.out.ScheduleGateway.SlotConfig;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.exception.ClassSlotScheduleMismatchException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Valida que un {@link ClassSlot} esté alineado con la grilla de turnos ({@code Schedule}) de su
 * cancha para su día de la semana: mismo {@code startTime} válido y misma {@code duration} que
 * {@code slotDuration} — no un múltiplo, igualdad exacta.
 * <p>
 * Cierra F17 (docs/software-review-2026-09-09.md, ver docs/loop/F17.md para el análisis completo):
 * sin esta validación, la detección de conflicto de {@code CourtOccupancyPort}/
 * {@code ClassRecurrencePort} (coincidencia EXACTA de {@code start_time}, no solapamiento real de
 * intervalos) deja de ser una garantía válida en cuanto un {@code ClassSlot} puede elegir cualquier
 * {@code startTime}/{@code duration} — para {@code Reservation} esa simplificación es correcta
 * porque {@code BookReservationUseCase} fuerza siempre {@code slotConfig.slotDuration()}, nunca un
 * valor arbitrario del cliente (ver {@code ScheduleGateway.SlotConfig}). Esta clase extiende esa
 * misma disciplina a {@code ClassSlot}, en el mismo punto de entrada (creación/reactivación), en vez
 * de reimplementar solapamiento real de rangos en {@code court_occupancy} — esa alternativa fue
 * comparada y descartada en el `PLAN` de docs/loop/F17.md porque exigiría abandonar, para el
 * invariante más peligroso del sistema, la disciplina de dos capas (lock + `UNIQUE` de base) que el
 * resto del proyecto sostiene.
 */
@Service
@RequiredArgsConstructor
public class ValidateClassSlotSchedule {

    private final ScheduleGateway scheduleGateway;

    /** Court ya debe estar lockeada por el caller, antes de la primera lectura consistente. */
    public void execute(ClassSlot slot) {
        SlotConfig config = scheduleGateway.findByCourtAndDay(slot.courtId(), slot.dayOfWeek())
                .orElseThrow(() -> new ScheduleNotFoundException(
                        "No hay agenda disponible para ese día en esta cancha"));

        if (!config.isValidSlot(slot.startTime()) || !config.slotDuration().equals(slot.duration())) {
            throw new ClassSlotScheduleMismatchException(
                    "El horario de la clase (inicio " + slot.startTime() + ", duración "
                            + slot.duration().toMinutes() + " minutos) debe coincidir exactamente con "
                            + "un turno de la agenda de la cancha para ese día: turnos de "
                            + config.slotDuration().toMinutes() + " minutos entre "
                            + config.openingTime() + " y " + config.closingTime());
        }
    }
}
