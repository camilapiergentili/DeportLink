package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.CourtGateway.CourtSnapshot;
import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.application.port.out.PlayerGateway.PlayerSnapshot;
import com.deportlink.deportlink.application.usecase.reservation.CancelReservationUseCase;
import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.model.TimeSlot;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.exception.CancellationTimeExceededException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import com.deportlink.deportlink.exception.ReservationNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelReservationUseCaseTest {

    @Mock private ReservationRepositoryPort reservationRepository;
    @Mock private PlayerGateway playerGateway;
    @Mock private CourtGateway courtGateway;

    @InjectMocks private CancelReservationUseCase useCase;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static final Long RESERVATION_ID = 1L;
    private static final Long COURT_ID = 10L;
    private static final Long PLAYER_ID = 20L;
    private static final Long OTHER_PLAYER_ID = 99L;

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(PLAYER_ID, "Juan", "Pérez");
    }

    private static CourtSnapshot courtWithWindow(int cancellationWindowHours) {
        return new CourtSnapshot(COURT_ID, 100.0, "Cancha 1", "Fútbol", "Sucursal Centro",
                "Av. Siempre Viva 123", cancellationWindowHours);
    }

    /** Reserva con anticipación fija respecto de "ahora" — evita depender de la hora real de ejecución. */
    private static Reservation reservationWithHoursUntil(long hoursFromNow, Long playerId) {
        LocalDateTime start = LocalDateTime.now().plusHours(hoursFromNow);
        TimeSlot slot = new TimeSlot(start.toLocalDate(), start.toLocalTime(), Duration.ofHours(1));
        return new Reservation(RESERVATION_ID, COURT_ID, playerId, slot, StatusReservation.RESERVADO, null);
    }

    // ─── Casos negativos ────────────────────────────────────────────────────────

    @Test
    void execute_playerInexistente_lanzaPlayerNotFoundYNoContinuaElFlujo() {
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID))
                .isInstanceOf(PlayerNotFoundException.class)
                .hasMessage("No se encontró el jugador");

        verifyNoInteractions(reservationRepository, courtGateway);
    }

    @Test
    void execute_reservaInexistente_lanzaReservationNotFoundYNoContinuaElFlujo() {
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessage("No se encontró la reserva");

        verifyNoInteractions(courtGateway);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void execute_reservaDeOtroJugador_lanzaReservationNotFound() {
        // IDOR: la reserva existe, pero es de otro jugador. Se devuelve el mismo
        // ReservationNotFoundException que "no existe" — no se filtra que existe ni de quién es.
        Reservation ajena = reservationWithHoursUntil(100, OTHER_PLAYER_ID);

        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessage("No se encontró la reserva");

        verifyNoInteractions(courtGateway);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void execute_courtInexistente_lanzaCourtNotFound() {
        Reservation existing = reservationWithHoursUntil(100, PLAYER_ID);

        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(existing));
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID))
                .isInstanceOf(CourtNotFoundException.class)
                .hasMessage("No se encontró la cancha");

        verify(reservationRepository, never()).save(any());
    }

    // ─── Happy path ─────────────────────────────────────────────────────────────

    @Test
    void execute_reservaValida_cancelaYPersisteElNuevoEstado() {
        Reservation existing = reservationWithHoursUntil(100, PLAYER_ID);
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);

        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(existing));
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(courtWithWindow(12)));
        when(reservationRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        Reservation result = useCase.execute(RESERVATION_ID, PLAYER_ID);

        assertThat(result.status()).isEqualTo(StatusReservation.CANCELADO);
        assertThat(captor.getValue().status()).isEqualTo(StatusReservation.CANCELADO);
        verify(reservationRepository).save(any());
    }

    // ─── La ventana usada es la del CourtSnapshot del mock, no un valor fijo ──────

    @Test
    void execute_usaLaVentanaDelCourtSnapshotDelMock_rechazaSiLaVentanaMockeadaEsMayorALaAnticipacion() {
        // ~100hs de anticipación, pero el mock devuelve una ventana de 200hs. Si el use case
        // usara cualquier otro valor (por ejemplo el 12 que Reservation tenía hardcodeado
        // antes de este refactor), esto NO lanzaría — 100 > 12. Que sí lance confirma que
        // efectivamente está usando los 200 del mock.
        Reservation existing = reservationWithHoursUntil(100, PLAYER_ID);

        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(existing));
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(courtWithWindow(200)));

        assertThatThrownBy(() -> useCase.execute(RESERVATION_ID, PLAYER_ID))
                .isInstanceOf(CancellationTimeExceededException.class)
                .hasMessageContaining("200");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void execute_usaLaVentanaDelCourtSnapshotDelMock_permiteCancelarSiLaVentanaMockeadaEsMenorALaAnticipacion() {
        // Mismas ~100hs de anticipación, ventana mockeada de 50hs — la dirección opuesta del
        // test anterior. Si el use case ignorara el mock y usara un valor fijo alto, esto
        // fallaría incorrectamente con CancellationTimeExceededException.
        Reservation existing = reservationWithHoursUntil(100, PLAYER_ID);

        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(existing));
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(courtWithWindow(50)));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Reservation result = useCase.execute(RESERVATION_ID, PLAYER_ID);

        assertThat(result.status()).isEqualTo(StatusReservation.CANCELADO);
    }
}
