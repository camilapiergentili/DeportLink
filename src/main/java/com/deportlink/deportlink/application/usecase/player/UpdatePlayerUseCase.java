package com.deportlink.deportlink.application.usecase.player;

import com.deportlink.deportlink.domain.model.Player;
import com.deportlink.deportlink.domain.port.out.PlayerRepositoryPort;
import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.exception.PlayerAlreadyExistsException;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatePlayerUseCase {

    private final PlayerRepositoryPort playerRepository;

    @Transactional
    public Player execute(Long id, PlayerRequestDto dto) {
        log.info("Updating player: playerId={}", id);

        Player existing = playerRepository.findById(id)
                .orElseThrow(() -> new PlayerNotFoundException("El jugador no se encontró"));

        playerRepository.findByEmail(dto.getEmail()).ifPresent(other -> {
            if (!other.id().equals(id)) {
                throw new PlayerAlreadyExistsException("El email ya está en uso");
            }
        });

        Player updated = new Player(existing.id(), dto.getFirstName(), dto.getLastName(),
                dto.getEmail(), dto.getPhone(), existing.password(), existing.addresses());

        Player saved = playerRepository.save(updated);
        log.info("Player updated: playerId={}", id);
        return saved;
    }
}