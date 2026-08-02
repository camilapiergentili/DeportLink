package com.deportlink.deportlink.application.usecase.reservation;

import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetPlayerReservationsUseCase {

    private final ReservationRepositoryPort reservationRepository;
    private final PlayerGateway playerGateway;

    @Transactional(readOnly = true)
    public List<Reservation> execute(Long playerId) {
        log.info("Getting reservations for playerId={}", playerId);

        playerGateway.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException("No se encontró el jugador"));

        List<Reservation> reservations = reservationRepository.findByPlayerId(playerId);
        log.info("Found {} reservations for playerId={}", reservations.size(), playerId);
        return reservations;
    }
}