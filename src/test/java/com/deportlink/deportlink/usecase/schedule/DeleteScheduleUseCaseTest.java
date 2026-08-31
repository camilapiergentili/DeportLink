package com.deportlink.deportlink.usecase.schedule;

import com.deportlink.deportlink.application.usecase.schedule.DeleteScheduleUseCase;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.ScheduleHasReservationsException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteScheduleUseCaseTest {

    @Mock private CourtRepositoryPort courtRepository;
    @Mock private ScheduleRepositoryPort scheduleRepository;

    private static final Long SCHEDULE_ID = 1L;
    private static final Long COURT_ID = 10L;

    private static Schedule schedule() {
        return new Schedule(SCHEDULE_ID, COURT_ID, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(22, 0), Duration.ofMinutes(60));
    }

    private static Court court() {
        return new Court(COURT_ID, "Cancha 1", 1000.0, 1L, 1L, "Fútbol", ActiveStatus.ACTIVE);
    }

    @Test
    void delete_horarioSinReservas_eliminaCorrectamente() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.of(schedule()));
        when(scheduleRepository.existsReservationForDay(COURT_ID, DayOfWeek.MONDAY)).thenReturn(false);

        new DeleteScheduleUseCase(courtRepository, scheduleRepository).execute(SCHEDULE_ID, COURT_ID);

        verify(scheduleRepository).delete(SCHEDULE_ID);
    }

    @Test
    void delete_horarioConReservas_lanzaScheduleHasReservationsException() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.of(schedule()));
        when(scheduleRepository.existsReservationForDay(COURT_ID, DayOfWeek.MONDAY)).thenReturn(true);

        assertThatThrownBy(() -> new DeleteScheduleUseCase(courtRepository, scheduleRepository).execute(SCHEDULE_ID, COURT_ID))
                .isInstanceOf(ScheduleHasReservationsException.class);

        verify(scheduleRepository, never()).delete(any());
    }

    @Test
    void delete_noEncontradoLanzaScheduleNotFound() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeleteScheduleUseCase(courtRepository, scheduleRepository).execute(SCHEDULE_ID, COURT_ID))
                .isInstanceOf(ScheduleNotFoundException.class);

        verify(scheduleRepository, never()).delete(any());
        verify(scheduleRepository, never()).existsReservationForDay(any(), any());
    }

    // ─── F4: lock pesimista sobre Court ─────────────────────────────────────────

    @Test
    void delete_courtNoEncontrada_lanzaCourtNotFoundSinTocarLaAgenda() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeleteScheduleUseCase(courtRepository, scheduleRepository).execute(SCHEDULE_ID, COURT_ID))
                .isInstanceOf(CourtNotFoundException.class);

        // El lock se verifica antes que cualquier lectura/escritura de la agenda —
        // si la cancha no existe, ni siquiera se llega a buscar el Schedule.
        verify(scheduleRepository, never()).findByIdAndCourtId(any(), any());
        verify(scheduleRepository, never()).existsReservationForDay(any(), any());
        verify(scheduleRepository, never()).delete(any());
    }

    @Test
    void delete_tomaElLockDeCourtAntesDeLeerLaAgendaYLasReservas() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.of(schedule()));
        when(scheduleRepository.existsReservationForDay(COURT_ID, DayOfWeek.MONDAY)).thenReturn(false);

        new DeleteScheduleUseCase(courtRepository, scheduleRepository).execute(SCHEDULE_ID, COURT_ID);

        InOrder inOrder = inOrder(courtRepository, scheduleRepository);
        inOrder.verify(courtRepository).findByIdForUpdate(COURT_ID);
        inOrder.verify(scheduleRepository).findByIdAndCourtId(SCHEDULE_ID, COURT_ID);
        inOrder.verify(scheduleRepository).existsReservationForDay(COURT_ID, DayOfWeek.MONDAY);
        inOrder.verify(scheduleRepository).delete(SCHEDULE_ID);
    }
}
