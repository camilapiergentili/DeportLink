package com.deportlink.deportlink.application.usecase.player;

import com.deportlink.deportlink.domain.model.Player;
import com.deportlink.deportlink.domain.port.out.PlayerRepositoryPort;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPlayerByIdUseCase {

    private final PlayerRepositoryPort playerRepository;

    @Transactional(readOnly = true)
    public Player execute(Long id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new PlayerNotFoundException("El jugador no se encontró"));
    }
}