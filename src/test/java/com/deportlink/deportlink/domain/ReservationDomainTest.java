package com.deportlink.deportlink.domain;

import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.model.TimeSlot;
import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.exception.CancellationTimeExceededException;
import com.deportlink.deportlink.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Reservation.cancel() ya no tiene ninguna ventana de cancelación hardcodeada — la recibe
 * como parámetro (ver Branch.cancellationWindowHours / CancelReservationUseCase). Estos tests
 * prueban ese parámetro con valores DISTINTOS a propósito, para confirmar que el
 * comportamiento escala con lo que se le pase y no quedó ningún 12 (ni ningún otro número)
 * escondido en el dominio.
 */
class ReservationDomainTest {

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 10, 0);

    private static Reservation reservationStartingAt(LocalDateTime slotStart, StatusReservation status) {
        TimeSlot slot = new TimeSlot(slotStart.toLocalDate(), slotStart.toLocalTime(), Duration.ofHours(1));
        return new Reservation(1L, 10L, 20L, slot, status, null);
    }

    // ─── cancel() — la ventana escala con el parámetro ─────────────────────────────

    @Test
    void cancel_ventana6_5h59DeAnticipacion_lanzaCancellationTimeExceeded() {
        Reservation reservation = reservationStartingAt(NOW.plusHours(5).plusMinutes(59), StatusReservation.RESERVADO);

        assertThatThrownBy(() -> reservation.cancel(NOW, 6))
                .isInstanceOf(CancellationTimeExceededException.class);
    }

    @Test
    void cancel_ventana6_6h01DeAnticipacion_cancelaCorrectamente() {
        Reservation reservation = reservationStartingAt(NOW.plusHours(6).plusMinutes(1), StatusReservation.RESERVADO);

        Reservation result = reservation.cancel(NOW, 6);

        assertThat(result.status()).isEqualTo(StatusReservation.CANCELADO);
    }

    @Test
    void cancel_ventana6_exactamente6HorasDeAnticipacion_cancelaCorrectamente() {
        // Límite exacto: "no menos de 6 horas" permite exactamente 6.
        Reservation reservation = reservationStartingAt(NOW.plusHours(6), StatusReservation.RESERVADO);

        Reservation result = reservation.cancel(NOW, 6);

        assertThat(result.status()).isEqualTo(StatusReservation.CANCELADO);
    }

    @Test
    void cancel_ventana24_23h59DeAnticipacion_lanzaCancellationTimeExceeded() {
        // Misma regla, ventana distinta (24hs en vez de 6) — confirma que escala con el
        // parámetro y no quedó un 12 (ni ningún otro valor) hardcodeado en algún lado.
        Reservation reservation = reservationStartingAt(NOW.plusHours(23).plusMinutes(59), StatusReservation.RESERVADO);

        assertThatThrownBy(() -> reservation.cancel(NOW, 24))
                .isInstanceOf(CancellationTimeExceededException.class);
    }

    @Test
    void cancel_ventana24_24h01DeAnticipacion_cancelaCorrectamente() {
        Reservation reservation = reservationStartingAt(NOW.plusHours(24).plusMinutes(1), StatusReservation.RESERVADO);

        Reservation result = reservation.cancel(NOW, 24);

        assertThat(result.status()).isEqualTo(StatusReservation.CANCELADO);
    }

    @Test
    void cancel_ventana1_59MinutosDeAnticipacion_lanzaCancellationTimeExceeded() {
        // Un tercer valor de ventana, bien chico, para terminar de despejar cualquier duda
        // de que el 6 o el 24 de los tests anteriores tengan algo de especial.
        Reservation reservation = reservationStartingAt(NOW.plusMinutes(59), StatusReservation.RESERVADO);

        assertThatThrownBy(() -> reservation.cancel(NOW, 1))
                .isInstanceOf(CancellationTimeExceededException.class);
    }

    @Test
    void cancel_ventana1_1h01DeAnticipacion_cancelaCorrectamente() {
        Reservation reservation = reservationStartingAt(NOW.plusHours(1).plusMinutes(1), StatusReservation.RESERVADO);

        Reservation result = reservation.cancel(NOW, 1);

        assertThat(result.status()).isEqualTo(StatusReservation.CANCELADO);
    }

    // ─── cancel() — el estado se valida antes que el tiempo ────────────────────────

    @Test
    void cancel_yaCancelada_lanzaInvalidStatusTransitionSinImportarLaVentana() {
        // Ventana enorme, a propósito: si el chequeo de tiempo se evaluara primero (o en vez
        // del de estado), esta reserva con muchísima anticipación jamás fallaría por tiempo —
        // así que si igual lanza, confirma que el chequeo de estado corre antes.
        Reservation reservation = reservationStartingAt(NOW.plusYears(1), StatusReservation.CANCELADO);

        assertThatThrownBy(() -> reservation.cancel(NOW, 999_999))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void cancel_yaFinalizada_lanzaInvalidStatusTransitionSinImportarLaVentana() {
        Reservation reservation = reservationStartingAt(NOW.plusYears(1), StatusReservation.FINALIZADO);

        assertThatThrownBy(() -> reservation.cancel(NOW, 999_999))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ─── markAsRescheduled() ────────────────────────────────────────────────────

    @Test
    void markAsRescheduled_reservada_pasaAReprogramado() {
        Reservation reservation = reservationStartingAt(NOW.plusDays(1), StatusReservation.RESERVADO);

        Reservation result = reservation.markAsRescheduled();

        assertThat(result.status()).isEqualTo(StatusReservation.REPROGRAMADO);
    }

    @Test
    void markAsRescheduled_yaCancelada_lanzaInvalidStatusTransition() {
        Reservation reservation = reservationStartingAt(NOW.plusDays(1), StatusReservation.CANCELADO);

        assertThatThrownBy(reservation::markAsRescheduled)
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void markAsRescheduled_yaFinalizada_lanzaInvalidStatusTransition() {
        Reservation reservation = reservationStartingAt(NOW.plusDays(1), StatusReservation.FINALIZADO);

        assertThatThrownBy(reservation::markAsRescheduled)
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void markAsRescheduled_yaReprogramada_lanzaInvalidStatusTransition() {
        Reservation reservation = reservationStartingAt(NOW.plusDays(1), StatusReservation.REPROGRAMADO);

        assertThatThrownBy(reservation::markAsRescheduled)
                .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
