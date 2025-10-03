package com.deportlink.DeportLink.model.entity;

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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "player_address",
            joinColumns = @JoinColumn(name = "id_player"),
            inverseJoinColumns = @JoinColumn(name = "id_address")
    )
    private Set<AddressEntity> addresses = new HashSet<>();

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ReservationEntity> reservations = new HashSet<>();
}
