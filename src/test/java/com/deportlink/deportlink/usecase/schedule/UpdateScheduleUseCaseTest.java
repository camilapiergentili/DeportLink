package com.deportlink.deportlink.usecase.schedule;

import com.deportlink.deportlink.application.usecase.schedule.UpdateScheduleUseCase;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort.ActiveSlot;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.InvalidReservationDataException;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;
import com.deportlink.deportlink.exception.ReservationNotUpdateException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No existía un test unitario para UpdateScheduleUseCase antes de F4 (auditoría
 * docs/software-review-2026-08-30.md). Se agrega junto con la protección de concurrencia:
 * cubre tanto el comportamiento funcional ya existente como el lock nuevo sobre Court.
 */
@ExtendWith(MockitoExtension.class)
class UpdateScheduleUseCaseTest {

    @Mock private CourtRepositoryPort courtRepository;
    @Mock private ScheduleRepositoryPort scheduleRepository;
    @Mock private ReservationRepositoryPort reservationRepository;

    private static final Long SCHEDULE_ID = 1L;
    private static final Long COURT_ID = 10L;

    private static Schedule schedule() {
        return new Schedule(SCHEDULE_ID, COURT_ID, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(22, 0), Duration.ofMinutes(60));
    }

    private static Court court() {
        return new Court(COURT_ID, "Cancha 1", 1000.0, 1L, 1L, "Fútbol", ActiveStatus.ACTIVE);
    }

    private UpdateScheduleUseCase useCase() {
        return new UpdateScheduleUseCase(courtRepository, scheduleRepository, reservationRepository);
    }

    @Test
    void update_sinReservasActivas_actualizaCorrectamente() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.of(schedule()));
        when(reservationRepository.findActiveByCourtAndDay(COURT_ID, DayOfWeek.MONDAY)).thenReturn(List.of());

        useCase().execute(SCHEDULE_ID, COURT_ID, "10:00", "20:00");

        verify(scheduleRepository).save(any(Schedule.class));
    }

    @Test
    void update_reservaActivaDentroDelNuevoRango_actualizaCorrectamente() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.of(schedule()));
        when(reservationRepository.findActiveByCourtAndDay(COURT_ID, DayOfWeek.MONDAY))
                .thenReturn(List.of(new ActiveSlot(LocalTime.of(11, 0), Duration.ofMinutes(60))));

        useCase().execute(SCHEDULE_ID, COURT_ID, "10:00", "20:00");

        verify(scheduleRepository).save(any(Schedule.class));
    }

    @Test
    void update_reservaActivaFueraDelNuevoRango_lanzaReservationNotUpdateException() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.of(schedule()));
        when(reservationRepository.findActiveByCourtAndDay(COURT_ID, DayOfWeek.MONDAY))
                .thenReturn(List.of(new ActiveSlot(LocalTime.of(21, 0), Duration.ofMinutes(60))));

        assertThatThrownBy(() -> useCase().execute(SCHEDULE_ID, COURT_ID, "10:00", "20:00"))
                .isInstanceOf(ReservationNotUpdateException.class);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void update_reservaConDatosIncompletos_lanzaInvalidReservationDataException() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.of(schedule()));
        when(reservationRepository.findActiveByCourtAndDay(COURT_ID, DayOfWeek.MONDAY))
                .thenReturn(List.of(new ActiveSlot(null, null)));

        assertThatThrownBy(() -> useCase().execute(SCHEDULE_ID, COURT_ID, "10:00", "20:00"))
                .isInstanceOf(InvalidReservationDataException.class);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void update_horarioInicioPosteriorAFin_lanzaInvalidTimeRangeException() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.of(schedule()));

        assertThatThrownBy(() -> useCase().execute(SCHEDULE_ID, COURT_ID, "20:00", "10:00"))
                .isInstanceOf(InvalidTimeRangeException.class);

        verify(reservationRepository, never()).findActiveByCourtAndDay(any(), any());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void update_agendaNoEncontrada_lanzaScheduleNotFoundException() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(SCHEDULE_ID, COURT_ID, "10:00", "20:00"))
                .isInstanceOf(ScheduleNotFoundException.class);

        verify(scheduleRepository, never()).save(any());
    }

    // ─── F4: lock pesimista sobre Court ─────────────────────────────────────────

    @Test
    void update_courtNoEncontrada_lanzaCourtNotFoundSinTocarLaAgenda() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(SCHEDULE_ID, COURT_ID, "10:00", "20:00"))
                .isInstanceOf(CourtNotFoundException.class);

        // El lock se verifica antes que cualquier lectura de la agenda o de las reservas —
        // si la cancha no existe, ni siquiera se llega a buscar el Schedule.
        verify(scheduleRepository, never()).findByIdAndCourtId(any(), any());
        verify(reservationRepository, never()).findActiveByCourtAndDay(any(), any());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void update_tomaElLockDeCourtAntesDeLeerLaAgendaYLasReservas() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleRepository.findByIdAndCourtId(SCHEDULE_ID, COURT_ID)).thenReturn(Optional.of(schedule()));
        when(reservationRepository.findActiveByCourtAndDay(COURT_ID, DayOfWeek.MONDAY)).thenReturn(List.of());

        useCase().execute(SCHEDULE_ID, COURT_ID, "10:00", "20:00");

        InOrder inOrder = inOrder(courtRepository, scheduleRepository, reservationRepository);
        inOrder.verify(courtRepository).findByIdForUpdate(COURT_ID);
        inOrder.verify(scheduleRepository).findByIdAndCourtId(SCHEDULE_ID, COURT_ID);
        inOrder.verify(reservationRepository).findActiveByCourtAndDay(COURT_ID, DayOfWeek.MONDAY);
        inOrder.verify(scheduleRepository).save(any(Schedule.class));
    }
}
