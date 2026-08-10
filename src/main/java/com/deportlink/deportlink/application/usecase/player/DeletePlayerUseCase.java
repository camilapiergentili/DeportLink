package com.deportlink.deportlink.application.usecase.player;

import com.deportlink.deportlink.domain.port.out.PlayerRepositoryPort;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeletePlayerUseCase {

    private final PlayerRepositoryPort playerRepository;

    @Transactional
    public void execute(Long id) {
        log.info("Deleting player: playerId={}", id);
        playerRepository.findById(id)
                .orElseThrow(() -> new PlayerNotFoundException("El jugador no se encontró"));
        playerRepository.delete(id);
        log.info("Player deleted: playerId={}", id);
    }
}