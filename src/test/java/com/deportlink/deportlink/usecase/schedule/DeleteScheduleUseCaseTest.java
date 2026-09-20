package com.deportlink.deportlink.usecase.schedule;

import com.deportlink.deportlink.application.usecase.schedule.DeleteScheduleUseCase;
import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.exception.ScheduleHasReservationsException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock private ScheduleRepositoryPort scheduleRepository;

    private static final Long SCHEDULE_ID = 1L;
    private static final Long COURT_ID = 10L;

    private static Schedule schedule() {
        return new Schedule(SCHEDULE_ID, COURT_ID, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(22, 0), Duration.ofMinutes(60));
    }

    @Test
    void delete_horarioSinReservas_eliminaCorrectamente() {
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.of(schedule()));
        when(scheduleRepository.existsReservationForDay(COURT_ID, DayOfWeek.MONDAY)).thenReturn(false);

        new DeleteScheduleUseCase(scheduleRepository).execute(SCHEDULE_ID, COURT_ID);

        verify(scheduleRepository).delete(SCHEDULE_ID);
    }

    @Test
    void delete_horarioConReservas_lanzaScheduleHasReservationsException() {
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.of(schedule()));
        when(scheduleRepository.existsReservationForDay(COURT_ID, DayOfWeek.MONDAY)).thenReturn(true);

        assertThatThrownBy(() -> new DeleteScheduleUseCase(scheduleRepository).execute(SCHEDULE_ID, COURT_ID))
                .isInstanceOf(ScheduleHasReservationsException.class);

        verify(scheduleRepository, never()).delete(any());
    }

    @Test
    void delete_noEncontradoLanzaScheduleNotFound() {
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeleteScheduleUseCase(scheduleRepository).execute(SCHEDULE_ID, COURT_ID))
                .isInstanceOf(ScheduleNotFoundException.class);

        verify(scheduleRepository, never()).delete(any());
        verify(scheduleRepository, never()).existsReservationForDay(any(), any());
    }
}
