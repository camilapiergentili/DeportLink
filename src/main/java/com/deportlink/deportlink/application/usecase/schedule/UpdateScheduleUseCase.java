package com.deportlink.deportlink.application.usecase.schedule;

import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.InvalidReservationDataException;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;
import com.deportlink.deportlink.exception.ReservationNotUpdateException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateScheduleUseCase {

    private final CourtRepositoryPort courtRepository;
    private final ScheduleRepositoryPort scheduleRepository;
    private final ReservationRepositoryPort reservationRepository;

    @Transactional
    public void execute(Long scheduleId, Long courtId, String openingNew, String closingNew) {
        log.info("Updating schedule: scheduleId={}, courtId={}", scheduleId, courtId);

        // Lock pesimista sobre la cancha — PRIMERA lectura de la transacción, antes de leer la
        // agenda o las reservas activas. Sin esto, la verificación de más abajo ("las reservas
        // activas de este día entran en el nuevo rango horario") podría correr contra un
        // snapshot desactualizado si hay una reserva confirmándose en paralelo para esta misma
        // cancha (mismo mecanismo que BookReservationUseCase / DeleteCourtUseCase). Comparte el
        // lock con BookReservationUseCase.courtGateway.findByIdForUpdate(courtId): las dos
        // transacciones compiten por la misma fila de Court y quedan serializadas entre sí.
        courtRepository.findByIdForUpdate(courtId)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));

        Schedule schedule = scheduleRepository.findByIdAndCourtId(scheduleId, courtId)
                .orElseThrow(() -> new ScheduleNotFoundException("No se encontró la agenda"));

        LocalTime opening = LocalTime.parse(openingNew);
        LocalTime closing = LocalTime.parse(closingNew);

        if (opening.isAfter(closing)) {
            throw new InvalidTimeRangeException("El horario de inicio no puede ser posterior al horario de fin");
        }

        List<ReservationRepositoryPort.ActiveSlot> active =
                reservationRepository.findActiveByCourtAndDay(courtId, schedule.day());

        for (ReservationRepositoryPort.ActiveSlot slot : active) {
            if (slot.startTime() == null || slot.duration() == null) {
                throw new InvalidReservationDataException("Una reserva tiene datos incompletos");
            }
            LocalTime end = slot.startTime().plusMinutes(slot.duration().toMinutes());
            if (slot.startTime().isBefore(opening) || end.isAfter(closing)) {
                throw new ReservationNotUpdateException("Los horarios reservados no estan dentro del nuevo rango horario");
            }
        }

        Schedule updated = new Schedule(schedule.id(), schedule.courtId(), schedule.day(),
                opening, closing, schedule.slotDuration());
        scheduleRepository.save(updated);
        log.info("Schedule updated: scheduleId={}", scheduleId);
    }
}