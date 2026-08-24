package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.CourtGateway.CourtSnapshot;
import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.application.port.out.PlayerGateway.PlayerSnapshot;
import com.deportlink.deportlink.application.port.out.ScheduleGateway;
import com.deportlink.deportlink.application.port.out.ScheduleGateway.SlotConfig;
import com.deportlink.deportlink.application.usecase.reservation.RescheduleReservationUseCase;
import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.model.TimeSlot;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.exception.InvalidStatusTransitionException;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import com.deportlink.deportlink.exception.ReservationNotFoundException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import com.deportlink.deportlink.exception.SlotNotAvailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

/**
 * Complementa RescheduleReservationConcurrencyTest: ese test prueba la carrera real contra
 * MySQL (y de paso el caso IDOR — reserva de otro jugador). Este test es puramente secuencial
 * (Mockito) y cubre las reglas de negocio del flujo que la concurrencia no ejercita: cada
 * excepción de validación y el happy path completo (reprogramar + nueva reserva + precio + ticket).
 */
@ExtendWith(MockitoExtension.class)
class RescheduleReservationUseCaseTest {

    @Mock private ReservationRepositoryPort reservationRepository;
    @Mock private CourtGateway courtGateway;
    @Mock private PlayerGateway playerGateway;
    @Mock private ScheduleGateway scheduleGateway;

    @InjectMocks private RescheduleReservationUseCase useCase;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static final Long RESERVATION_ID = 1L;
    private static final Long COURT_ID = 10L;
    private static final Long PLAYER_ID = 20L;

    private static final LocalDate NEW_DAY = LocalDate.now().plusDays(10);
    private static final LocalTime NEW_START_TIME = LocalTime.of(10, 0);

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(PLAYER_ID, "Juan", "Pérez");
    }

    private static CourtSnapshot court() {
        return new CourtSnapshot(COURT_ID, 100.0, "Cancha 1", "Fútbol", "Sucursal Centro",
                "Av. Siempre Viva 123", 12);
    }

    /** Agenda 09:00–13:00 en slots de 1h — turnos válidos: 09, 10, 11, 12. */
    private static SlotConfig slotConfig() {
        return new SlotConfig(LocalTime.of(9, 0), LocalTime.of(13, 0), Duration.ofHours(1));
    }

    private static Reservation existingReservation(StatusReservation status, Long playerId) {
        TimeSlot slot = new TimeSlot(LocalDate.now().plusDays(5), LocalTime.of(9, 0), Duration.ofHours(1));
        return new Reservation(RESERVATION_ID, COURT_ID, playerId, slot, status, null);
    }

    /** Encadena los mocks felices hasta justo antes del punto que cada test quiere cortar. */
    private void mockUntilCourtLockAndPlayerAndReservation(Reservation existing) {
        when(courtGateway.findCourtIdByReservationForUpdate(RESERVATION_ID)).thenReturn(Optional.of(COURT_ID));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(existing));
    }

    // ─── Casos negativos ────────────────────────────────────────────────────────

    @Test
    void execute_playerInexistente_lanzaPlayerNotFoundYNoContinuaElFlujo() {
        when(courtGateway.findCourtIdByReservationForUpdate(RESERVATION_ID)).thenReturn(Optional.of(COURT_ID));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID, NEW_DAY, NEW_START_TIME))
                .isInstanceOf(PlayerNotFoundException.class)
                .hasMessage("No se encontró el jugador");

        verifyNoInteractions(reservationRepository, scheduleGateway);
        verify(courtGateway, never()).findByIdForUpdate(any());
    }

    @Test
    void execute_reservaInexistente_lanzaReservationNotFoundYNoContinuaElFlujo() {
        // Distinto del caso IDOR (ya cubierto en RescheduleReservationConcurrencyTest): acá la
        // reserva directamente no existe — el lock por JOIN de findCourtIdByReservationForUpdate
        // no encuentra nada, y ese es el primer chequeo del flujo.
        when(courtGateway.findCourtIdByReservationForUpdate(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID, NEW_DAY, NEW_START_TIME))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessage("No se encontró la reserva");

        verifyNoInteractions(playerGateway, reservationRepository, scheduleGateway);
        verify(courtGateway, never()).findByIdForUpdate(any());
    }

    @Test
    void execute_canchaDeLaReservaInexistente_lanzaReservationNotFound() {
        // Rama defensiva: el courtId vino del JOIN, pero el snapshot completo ya no existe.
        Reservation existing = existingReservation(StatusReservation.RESERVADO, PLAYER_ID);
        mockUntilCourtLockAndPlayerAndReservation(existing);
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID, NEW_DAY, NEW_START_TIME))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessage("La cancha de la reserva no existe");

        verifyNoInteractions(scheduleGateway);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void execute_sinAgendaParaEseDia_lanzaScheduleNotFound() {
        Reservation existing = existingReservation(StatusReservation.RESERVADO, PLAYER_ID);
        mockUntilCourtLockAndPlayerAndReservation(existing);
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, NEW_DAY.getDayOfWeek())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID, NEW_DAY, NEW_START_TIME))
                .isInstanceOf(ScheduleNotFoundException.class)
                .hasMessage("No hay agenda disponible para ese día");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void execute_horarioFueraDeAgenda_lanzaSlotNotAvailable() {
        Reservation existing = existingReservation(StatusReservation.RESERVADO, PLAYER_ID);
        mockUntilCourtLockAndPlayerAndReservation(existing);
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, NEW_DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));

        LocalTime outOfSchedule = LocalTime.of(14, 0); // agenda cierra a las 13

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID, NEW_DAY, outOfSchedule))
                .isInstanceOf(SlotNotAvailableException.class)
                .hasMessage("El horario no corresponde a un turno válido");

        verify(reservationRepository, never()).findBookedSlots(any(), any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void execute_horarioYaReservado_lanzaSlotNotAvailable() {
        Reservation existing = existingReservation(StatusReservation.RESERVADO, PLAYER_ID);
        mockUntilCourtLockAndPlayerAndReservation(existing);
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, NEW_DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, NEW_DAY)).thenReturn(Set.of(NEW_START_TIME));

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID, NEW_DAY, NEW_START_TIME))
                .isInstanceOf(SlotNotAvailableException.class)
                .hasMessage("El horario ya está reservado");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void execute_reservaYaNoEstaReservada_lanzaInvalidStatusTransition() {
        // La reserva llega hasta acá (todos los chequeos previos pasan) pero ya no está
        // RESERVADO — existing.markAsRescheduled() es dominio real, no mockeado, y debe
        // propagar su propia excepción sin que el use case la intercepte.
        Reservation existing = existingReservation(StatusReservation.CANCELADO, PLAYER_ID);
        mockUntilCourtLockAndPlayerAndReservation(existing);
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, NEW_DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, NEW_DAY)).thenReturn(Set.of());

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID, NEW_DAY, NEW_START_TIME))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Solo una reserva RESERVADO puede reprogramarse");

        verify(reservationRepository, never()).save(any());
    }

    // ─── Happy path ─────────────────────────────────────────────────────────────

    @Test
    void execute_flujoValido_reprogramaLaViejaYCreaLaNuevaConPrecioYTicketCorrectos() {
        Reservation existing = existingReservation(StatusReservation.RESERVADO, PLAYER_ID);
        mockUntilCourtLockAndPlayerAndReservation(existing);
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, NEW_DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, NEW_DAY)).thenReturn(Set.of());

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        when(reservationRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        Reservation result = useCase.execute(RESERVATION_ID, PLAYER_ID, NEW_DAY, NEW_START_TIME);

        List<Reservation> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);

        // Primer save: la reserva vieja, marcada REPROGRAMADO.
        Reservation oldSaved = saved.get(0);
        assertThat(oldSaved.id()).isEqualTo(RESERVATION_ID);
        assertThat(oldSaved.status()).isEqualTo(StatusReservation.REPROGRAMADO);

        // Segundo save: la reserva nueva, RESERVADO, con el slot/court/player correctos y ticket.
        Reservation newSaved = saved.get(1);
        assertThat(newSaved.status()).isEqualTo(StatusReservation.RESERVADO);
        assertThat(newSaved.courtId()).isEqualTo(COURT_ID);
        assertThat(newSaved.playerId()).isEqualTo(PLAYER_ID);
        assertThat(newSaved.timeSlot().day()).isEqualTo(NEW_DAY);
        assertThat(newSaved.timeSlot().startTime()).isEqualTo(NEW_START_TIME);

        assertThat(newSaved.ticket()).isNotNull();
        assertThat(newSaved.ticket().playerName()).isEqualTo("Juan");
        assertThat(newSaved.ticket().playerLastName()).isEqualTo("Pérez");
        assertThat(newSaved.ticket().courtName()).isEqualTo("Cancha 1");
        assertThat(newSaved.ticket().sport()).isEqualTo("Fútbol");
        assertThat(newSaved.ticket().branchName()).isEqualTo("Sucursal Centro");
        assertThat(newSaved.ticket().branchAddress()).isEqualTo("Av. Siempre Viva 123");
        // 1 hora de slot * 100.0 por hora
        assertThat(newSaved.ticket().totalPrice()).isEqualTo(100.0);

        assertThat(result).isEqualTo(newSaved);
    }
}
