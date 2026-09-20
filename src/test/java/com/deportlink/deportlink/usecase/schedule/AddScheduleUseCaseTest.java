package com.deportlink.deportlink.usecase.schedule;

import com.deportlink.deportlink.application.usecase.schedule.AddScheduleUseCase;
import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.dto.request.ScheduleRequestDto;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.exception.ClubNotActivedException;
import com.deportlink.deportlink.exception.ClubNotApprovedException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;
import com.deportlink.deportlink.exception.ScheduleAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No existía un test unitario para AddScheduleUseCase antes de F16
 * (docs/software-review-2026-08-31.md) — se agrega junto con la protección de concurrencia. Cubre
 * el comportamiento funcional ya existente y el lock nuevo sobre Court. El propósito de este test
 * es documentar el ORDEN de invocación (findByIdForUpdate antes que cualquier otra lectura) — que
 * el lock realmente serialice transacciones concurrentes es responsabilidad de
 * AddScheduleConcurrencyTest (Testcontainers/MySQL real), no de este test con Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AddScheduleUseCaseTest {

    @Mock private CourtRepositoryPort courtRepository;
    @Mock private BranchRepositoryPort branchRepository;
    @Mock private ScheduleRepositoryPort scheduleRepository;

    private static final Long COURT_ID = 10L;
    private static final Long BRANCH_ID = 20L;

    private static Court activeCourt() {
        return new Court(COURT_ID, "Cancha 1", 1000.0, BRANCH_ID, 1L, "Fútbol", ActiveStatus.ACTIVE);
    }

    private static Branch approvedBranch() {
        Address address = new Address("Calle Falsa", 123, "CABA", "Buenos Aires", 1000, -34.6, -58.4);
        return new Branch(BRANCH_ID, "Sucursal 1", address, 1L,
                VerificationStatus.APPROVED, ActiveStatus.ACTIVE, 12);
    }

    private static ScheduleRequestDto dto(String day, String opening, String closing) {
        ScheduleRequestDto dto = new ScheduleRequestDto();
        dto.setDay(day);
        dto.setOpeningTime(opening);
        dto.setClosingTime(closing);
        dto.setSlotDuration(60L);
        return dto;
    }

    private AddScheduleUseCase useCase() {
        return new AddScheduleUseCase(courtRepository, branchRepository, scheduleRepository);
    }

    @Test
    void add_sinConflictos_guardaElHorarioNuevo() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        when(scheduleRepository.findAllByCourtId(COURT_ID)).thenReturn(List.of());

        useCase().execute(COURT_ID, List.of(dto("MONDAY", "9:00", "22:00")));

        verify(scheduleRepository).saveAll(anyList());
    }

    @Test
    void add_solapaConHorarioExistenteDelMismoDia_lanzaScheduleAlreadyExistsException() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        Schedule existing = new Schedule(1L, COURT_ID, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(22, 0), Duration.ofMinutes(60));
        when(scheduleRepository.findAllByCourtId(COURT_ID)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> useCase().execute(COURT_ID, List.of(dto("MONDAY", "10:00", "20:00"))))
                .isInstanceOf(ScheduleAlreadyExistsException.class);

        verify(scheduleRepository, never()).saveAll(any());
    }

    @Test
    void add_horarioInicioPosteriorAFin_lanzaInvalidTimeRangeException() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));

        assertThatThrownBy(() -> useCase().execute(COURT_ID, List.of(dto("MONDAY", "20:00", "10:00"))))
                .isInstanceOf(InvalidTimeRangeException.class);

        verify(scheduleRepository, never()).findAllByCourtId(any());
        verify(scheduleRepository, never()).saveAll(any());
    }

    @Test
    void add_canchaInactiva_lanzaClubNotActivedExceptionSinTocarLaSucursal() {
        Court inactive = new Court(COURT_ID, "Cancha 1", 1000.0, BRANCH_ID, 1L, "Fútbol", ActiveStatus.INACTIVE);
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> useCase().execute(COURT_ID, List.of(dto("MONDAY", "9:00", "22:00"))))
                .isInstanceOf(ClubNotActivedException.class);

        verify(branchRepository, never()).findById(any());
        verify(scheduleRepository, never()).saveAll(any());
    }

    @Test
    void add_sucursalNoAprobada_lanzaClubNotApprovedException() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        Address address = new Address("Calle Falsa", 123, "CABA", "Buenos Aires", 1000, -34.6, -58.4);
        Branch pending = new Branch(BRANCH_ID, "Sucursal 1", address, 1L,
                VerificationStatus.PENDING, ActiveStatus.INACTIVE, 12);
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase().execute(COURT_ID, List.of(dto("MONDAY", "9:00", "22:00"))))
                .isInstanceOf(ClubNotApprovedException.class);

        verify(scheduleRepository, never()).saveAll(any());
    }

    @Test
    void add_sucursalNoEncontrada_lanzaBranchNotFoundException() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(COURT_ID, List.of(dto("MONDAY", "9:00", "22:00"))))
                .isInstanceOf(BranchNotFoundException.class);

        verify(scheduleRepository, never()).saveAll(any());
    }

    // ─── F16: lock pesimista sobre Court ────────────────────────────────────────

    @Test
    void add_canchaNoEncontrada_lanzaCourtNotFoundSinTocarLaSucursalNiLaAgenda() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(COURT_ID, List.of(dto("MONDAY", "9:00", "22:00"))))
                .isInstanceOf(CourtNotFoundException.class);

        // El lock se verifica antes que cualquier otra lectura — si la cancha no existe, ni
        // siquiera se llega a buscar la sucursal ni la agenda existente.
        verify(branchRepository, never()).findById(any());
        verify(scheduleRepository, never()).findAllByCourtId(any());
        verify(scheduleRepository, never()).saveAll(any());
    }

    @Test
    void add_tomaElLockDeCourtAntesDeLeerLaAgendaExistente() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        when(scheduleRepository.findAllByCourtId(COURT_ID)).thenReturn(List.of());

        useCase().execute(COURT_ID, List.of(dto("MONDAY", "9:00", "22:00")));

        InOrder inOrder = inOrder(courtRepository, scheduleRepository);
        inOrder.verify(courtRepository).findByIdForUpdate(COURT_ID);
        inOrder.verify(scheduleRepository).findAllByCourtId(COURT_ID);
        inOrder.verify(scheduleRepository).saveAll(anyList());
    }
}
