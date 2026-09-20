package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.application.port.out.PlayerGateway.PlayerSnapshot;
import com.deportlink.deportlink.application.usecase.reservation.GetPlayerReservationsUseCase;
import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.model.TimeSlot;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetPlayerReservationsUseCaseTest {

    @Mock private ReservationRepositoryPort reservationRepository;
    @Mock private PlayerGateway playerGateway;

    @InjectMocks private GetPlayerReservationsUseCase useCase;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static final Long PLAYER_ID = 20L;

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(PLAYER_ID, "Juan", "Pérez");
    }

    private static Reservation reservation(Long id, StatusReservation status) {
        TimeSlot slot = new TimeSlot(LocalDate.now().plusDays(5), LocalTime.of(10, 0), Duration.ofHours(1));
        return new Reservation(id, 10L, PLAYER_ID, slot, status, null);
    }

    // ─── Casos negativos ────────────────────────────────────────────────────────

    @Test
    void execute_playerInexistente_lanzaPlayerNotFoundYNoContinuaElFlujo() {
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(PLAYER_ID))
                .isInstanceOf(PlayerNotFoundException.class)
                .hasMessage("No se encontró el jugador");

        verifyNoInteractions(reservationRepository);
    }

    // ─── Happy path ─────────────────────────────────────────────────────────────

    @Test
    void execute_jugadorConReservas_devuelveLaListaDelRepositorio() {
        List<Reservation> reservations = List.of(
                reservation(1L, StatusReservation.RESERVADO),
                reservation(2L, StatusReservation.CANCELADO));

        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(reservationRepository.findByPlayerId(PLAYER_ID)).thenReturn(reservations);

        List<Reservation> result = useCase.execute(PLAYER_ID);

        assertThat(result).isEqualTo(reservations);
    }

    @Test
    void execute_jugadorSinReservas_devuelveListaVacia() {
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(reservationRepository.findByPlayerId(PLAYER_ID)).thenReturn(List.of());

        List<Reservation> result = useCase.execute(PLAYER_ID);

        assertThat(result).isEmpty();
    }
}
