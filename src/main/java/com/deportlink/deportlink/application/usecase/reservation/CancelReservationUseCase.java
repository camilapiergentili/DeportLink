package com.deportlink.deportlink.application.usecase.reservation;

import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.CourtGateway.CourtSnapshot;
import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import com.deportlink.deportlink.exception.ReservationNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelReservationUseCase {

    private final ReservationRepositoryPort reservationRepository;
    private final PlayerGateway playerGateway;
    private final CourtGateway courtGateway;

    @Transactional
    public Reservation execute(Long reservationId, Long playerId) {
        log.info("Cancelling reservation: reservationId={}, playerId={}", reservationId, playerId);

        playerGateway.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("No se encontró el jugador"));

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("No se encontró la reserva"));

        // belongsTo is a domain query — the aggregate knows who owns it.
        // We throw ReservationNotFound (not Unauthorized) to avoid leaking reservation existence to other players.
        if (!reservation.belongsTo(playerId)) {
            throw new ReservationNotFoundException("No se encontró la reserva");
        }

        // La ventana de cancelación es propiedad de la sucursal de la cancha, no de la
        // reserva — se resuelve acá, con el valor VIGENTE al momento de cancelar (no el que
        // regía cuando se reservó). Sin lock: cancelar no compite por el mismo slot que un
        // booking nuevo, no hay una carrera equivalente a la de Book/Reschedule acá.
        CourtSnapshot court = courtGateway.findById(reservation.courtId())
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));

        // cancel() es un método de dominio — valida la ventana de cancelación (recibida acá,
        // no hardcodeada) y la transición de estado. Si alguna falla, tira una excepción de
        // dominio, manteniendo la regla en un solo lugar.
        Reservation cancelled = reservation.cancel(LocalDateTime.now(), court.cancellationWindowHours());

        Reservation saved = reservationRepository.save(cancelled);
        log.info("Reservation cancelled: id={}", saved.id());
        return saved;
    }
}