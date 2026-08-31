package com.deportlink.deportlink.application.usecase.schedule;

import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.dto.request.ScheduleRequestDto;
import com.deportlink.deportlink.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddScheduleUseCase {

    private final CourtRepositoryPort courtRepository;
    private final BranchRepositoryPort branchRepository;
    private final ScheduleRepositoryPort scheduleRepository;

    @Transactional
    public void execute(Long courtId, List<ScheduleRequestDto> scheduleDtos) {
        log.info("Adding schedules for court: courtId={}, count={}", courtId, scheduleDtos.size());

        // Lock pesimista sobre la cancha — PRIMERA lectura de la transacción, antes de leer los
        // horarios existentes o decidir si el nuevo horario solapa con alguno. Sin esto, dos altas
        // concurrentes para la misma cancha pueden leer ambas el mismo estado de `availability`
        // antes de que cualquiera inserte, pasar juntas filterConflicts() y terminar insertando dos
        // filas para el mismo (court_id, day_of_week) — lo que rompe con un 500 la próxima consulta
        // de disponibilidad/reserva para esa cancha/día (findByCourtIdAndDay espera 0 o 1 fila).
        // Mismo patrón que BookReservationUseCase/RescheduleReservationUseCase/DeleteCourtUseCase/
        // DeleteBranchUseCase/UpdateScheduleUseCase/DeleteScheduleUseCase: todas compiten por la
        // misma fila de Court y quedan serializadas entre sí. Ver docs/software-review-2026-08-31.md,
        // finding F16.
        Court court = courtRepository.findByIdForUpdate(courtId)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));

        if (!court.isActive()) {
            throw new ClubNotActivedException("No puede agregar agenda, porque la cancha no se encuentra activa");
        }

        Branch branch = branchRepository.findById(court.branchId())
                .orElseThrow(() -> new BranchNotFoundException("No se encontró la sucursal"));

        if (!branch.isApproved()) {
            throw new ClubNotApprovedException("No puede agregar agenda, porque la cancha no se encuentra aprobada");
        }

        List<Schedule> newSchedules = toSchedules(courtId, scheduleDtos);
        validateTimeRanges(newSchedules);

        List<Schedule> existing = scheduleRepository.findAllByCourtId(courtId);
        List<Schedule> unique = filterConflicts(newSchedules, existing, court.name());

        scheduleRepository.saveAll(unique);
        log.info("Schedules added for court: courtId={}", courtId);
    }

    private List<Schedule> toSchedules(Long courtId, List<ScheduleRequestDto> dtos) {
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("H:mm");
        return dtos.stream().map(dto -> new Schedule(
                null,
                courtId,
                DayOfWeek.valueOf(dto.getDay().toUpperCase()),
                LocalTime.parse(dto.getOpeningTime(), timeFormat),
                LocalTime.parse(dto.getClosingTime(), timeFormat),
                Duration.ofMinutes(dto.getSlotDuration())
        )).toList();
    }

    private void validateTimeRanges(List<Schedule> schedules) {
        for (Schedule s : schedules) {
            if (s.openingTime().isAfter(s.closingTime())) {
                throw new InvalidTimeRangeException("El horario de inicio no puede ser posterior al horario de fin");
            }
        }
    }

    private List<Schedule> filterConflicts(List<Schedule> incoming, List<Schedule> existing, String courtName) {
        List<Schedule> unique = incoming.stream()
                .filter(n -> existing.stream().noneMatch(e ->
                        e.day().equals(n.day()) && overlaps(e, n)))
                .toList();

        if (unique.isEmpty()) {
            throw new ScheduleAlreadyExistsException("Los horarios ya existen para la cancha " + courtName);
        }
        return unique;
    }

    private boolean overlaps(Schedule a, Schedule b) {
        return !(a.closingTime().isBefore(b.openingTime()) || a.openingTime().isAfter(b.closingTime()));
    }
}