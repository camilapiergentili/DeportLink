package com.deportlink.deportlink.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "players")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerEntity extends UserEntity {

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "id_player")
    private Set<AddressEntity> addresses = new HashSet<>();

    // Sin cascade ni orphanRemoval: Reservation representa historial y no debe
    // eliminarse como efecto secundario de borrar/anonimizar un Player.
    // Reservation se persiste directamente vía ReservationRepositoryAdapter, no a
    // través de esta colección — quitar el cascade no afecta la creación de reservas.
    @OneToMany(mappedBy = "player")
    private Set<ReservationEntity> reservations = new HashSet<>();
}
