package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.Player;

import java.util.Optional;

public interface PlayerRepositoryPort {
    Player save(Player player);
    Optional<Player> findById(Long id);
    Optional<Player> findByEmail(String email);
    void delete(Long id);
    boolean existsByEmail(String email);
}