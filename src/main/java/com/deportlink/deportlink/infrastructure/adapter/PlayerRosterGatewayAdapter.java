package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.application.port.out.PlayerRosterGateway;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;

@Component
@RequiredArgsConstructor
public class PlayerRosterGatewayAdapter implements PlayerRosterGateway {

    private final PlayerRepository playerRepository;

    @Override
    public Map<Long, PlayerGateway.PlayerSnapshot> findByIds(Set<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        return playerRepository.findAllById(playerIds).stream()
                .collect(toMap(PlayerEntity::getId, this::toSnapshot));
    }

    private PlayerGateway.PlayerSnapshot toSnapshot(PlayerEntity entity) {
        return new PlayerGateway.PlayerSnapshot(entity.getId(), entity.getFirstName(), entity.getLastName());
    }
}
