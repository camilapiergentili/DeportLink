package com.deportlink.deportlink.application.usecase.schedule;

import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.ScheduleHasReservationsException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteScheduleUseCase {

    private final CourtRepositoryPort courtRepository;
    private final ScheduleRepositoryPort scheduleRepository;

    @Transactional
    public void execute(Long scheduleId, Long courtId) {
        log.info("Deleting schedule: scheduleId={}, courtId={}", scheduleId, courtId);

        // Lock pesimista sobre la cancha — PRIMERA lectura de la transacción, antes de chequear
        // reservas o tocar la agenda. Mismo mecanismo y mismo recurso compartido con
        // BookReservationUseCase que en UpdateScheduleUseCase/DeleteCourtUseCase: sin este lock,
        // existsReservationForDay() podría verificarse contra un snapshot desactualizado si hay
        // una reserva confirmándose en paralelo para esta misma cancha/día.
        courtRepository.findByIdForUpdate(courtId)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));

        Schedule schedule = scheduleRepository.findByIdAndCourtId(scheduleId, courtId)
                .orElseThrow(() -> new ScheduleNotFoundException("No se encontró la agenda"));

        if (scheduleRepository.existsReservationForDay(courtId, schedule.day())) {
            throw new ScheduleHasReservationsException("No se puede eliminar, ya que existen reservas para ese dia");
        }

        scheduleRepository.delete(scheduleId);
        log.info("Schedule deleted: scheduleId={}", scheduleId);
    }
}