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

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ReservationEntity> reservations = new HashSet<>();
}
