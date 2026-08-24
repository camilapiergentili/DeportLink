package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.CourtGateway.CourtSnapshot;
import com.deportlink.deportlink.application.port.out.ScheduleGateway;
import com.deportlink.deportlink.application.port.out.ScheduleGateway.SlotConfig;
import com.deportlink.deportlink.application.usecase.reservation.GetAvailableSlotsUseCase;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetAvailableSlotsUseCaseTest {

    @Mock private CourtGateway courtGateway;
    @Mock private ScheduleGateway scheduleGateway;
    @Mock private ReservationRepositoryPort reservationRepository;

    @InjectMocks private GetAvailableSlotsUseCase useCase;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static final Long COURT_ID = 10L;
    private static final LocalDate DAY = LocalDate.of(2026, 8, 31); // lunes

    private static CourtSnapshot court() {
        return new CourtSnapshot(COURT_ID, 100.0, "Cancha 1", "Fútbol", "Sucursal Centro",
                "Av. Siempre Viva 123", 12);
    }

    /** Agenda 09:00–13:00 en slots de 1h — genera 09, 10, 11, 12. */
    private static SlotConfig slotConfig() {
        return new SlotConfig(LocalTime.of(9, 0), LocalTime.of(13, 0), Duration.ofHours(1));
    }

    // ─── Casos negativos ────────────────────────────────────────────────────────

    @Test
    void execute_courtInexistente_lanzaCourtNotFoundYNoContinuaElFlujo() {
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(COURT_ID, DAY))
                .isInstanceOf(CourtNotFoundException.class)
                .hasMessage("No se encontró la cancha");

        verifyNoInteractions(scheduleGateway, reservationRepository);
    }

    @Test
    void execute_sinAgendaParaEseDia_lanzaScheduleNotFound() {
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(COURT_ID, DAY))
                .isInstanceOf(ScheduleNotFoundException.class)
                .hasMessage("No hay agenda disponible para ese día");

        verify(reservationRepository, never()).findBookedSlots(any(), any());
    }

    // ─── Happy path ─────────────────────────────────────────────────────────────

    @Test
    void execute_sinReservas_devuelveTodosLosSlots() {
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of());

        List<LocalTime> result = useCase.execute(COURT_ID, DAY);

        assertThat(result).containsExactly(
                LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0));
    }

    @Test
    void execute_diaCompletamenteReservado_devuelveListaVacia() {
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of(
                LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0)));

        List<LocalTime> result = useCase.execute(COURT_ID, DAY);

        assertThat(result).isEmpty();
    }

    @Test
    void execute_diaParcialmenteReservado_devuelveSoloLosSlotsLibres() {
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of(LocalTime.of(10, 0)));

        List<LocalTime> result = useCase.execute(COURT_ID, DAY);

        assertThat(result).containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0), LocalTime.of(12, 0));
    }
}
