package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.Player;
import com.deportlink.deportlink.domain.model.PlayerAddress;
import com.deportlink.deportlink.domain.port.out.PlayerRepositoryPort;
import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import com.deportlink.deportlink.model.Rol;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PlayerRepositoryAdapter implements PlayerRepositoryPort {

    private final PlayerRepository playerRepository;

    @Override
    public Player save(Player player) {
        PlayerEntity entity;
        if (player.id() != null) {
            entity = playerRepository.findById(player.id())
                    .orElseThrow(() -> new PlayerNotFoundException("El jugador no se encontró"));
            entity.setFirstName(player.firstName());
            entity.setLastName(player.lastName());
            entity.setEmail(player.email());
            entity.setPhone(player.phone());
        } else {
            entity = new PlayerEntity();
            entity.setFirstName(player.firstName());
            entity.setLastName(player.lastName());
            entity.setEmail(player.email());
            entity.setPhone(player.phone());
            entity.setPassword(player.password());
            entity.setRole(Rol.PLAYER);
            Set<AddressEntity> addresses = player.addresses().stream()
                    .map(this::toAddressEntity).collect(Collectors.toSet());
            entity.setAddresses(addresses);
        }
        return toPlayer(playerRepository.save(entity));
    }

    @Override
    public Optional<Player> findById(Long id) {
        return playerRepository.findById(id).map(this::toPlayer);
    }

    @Override
    public Optional<Player> findByEmail(String email) {
        return playerRepository.findByEmail(email).map(this::toPlayer);
    }

    @Override
    public void delete(Long id) {
        PlayerEntity entity = playerRepository.findById(id)
                .orElseThrow(() -> new PlayerNotFoundException("El jugador no se encontró"));
        entity.getReservations().stream()
                .filter(r -> r.getStatus().occupiesSlot())
                .forEach(r -> r.setStatus(StatusReservation.CANCELADO));
        playerRepository.save(entity);
        playerRepository.delete(entity);
    }

    @Override
    public boolean existsByEmail(String email) {
        return playerRepository.findByEmail(email).isPresent();
    }

    private AddressEntity toAddressEntity(PlayerAddress addr) {
        AddressEntity e = new AddressEntity();
        e.setStreetName(addr.streetName());
        e.setNumber(addr.number());
        e.setCity(addr.city());
        e.setProvince(addr.province());
        e.setPostalCode(addr.postalCode());
        e.setLatitude(addr.latitude());
        e.setLongitude(addr.longitude());
        e.setDefault(addr.isDefault());
        return e;
    }

    private Player toPlayer(PlayerEntity entity) {
        Set<PlayerAddress> addresses = entity.getAddresses().stream()
                .map(a -> new PlayerAddress(a.getId(), a.getStreetName(), a.getNumber(),
                        a.getCity(), a.getProvince(), a.getPostalCode(),
                        a.getLatitude(), a.getLongitude(), a.isDefault()))
                .collect(Collectors.toSet());
        return new Player(entity.getId(), entity.getFirstName(), entity.getLastName(),
                entity.getEmail(), entity.getPhone(), null, addresses);
    }
}