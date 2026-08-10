package com.deportlink.deportlink.application.usecase.reservation;

import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
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

        // cancel() is a domain method — it validates the 12-hour window and status transition.
        // If either check fails, it throws a domain exception, keeping the rule in one place.
        Reservation cancelled = reservation.cancel(LocalDateTime.now());

        Reservation saved = reservationRepository.save(cancelled);
        log.info("Reservation cancelled: id={}", saved.id());
        return saved;
    }
}