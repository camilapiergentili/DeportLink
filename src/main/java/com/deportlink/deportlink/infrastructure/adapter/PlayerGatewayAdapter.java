package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PlayerGatewayAdapter implements PlayerGateway {

    private final PlayerRepository playerRepository;

    @Override
    public Optional<PlayerSnapshot> findById(Long id) {
        return playerRepository.findById(id).map(entity ->
                new PlayerSnapshot(entity.getId(), entity.getFirstName(), entity.getLastName())
        );
    }
}