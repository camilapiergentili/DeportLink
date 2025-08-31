package com.deportlink.DeportLink.persistence.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name= "court")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CourtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String name;
    private double pricePerHour;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private UserEntity owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sport_id")
    private SportEntity sport;

    @OneToMany(mappedBy = "courtEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AddressEntity> address = new HashSet<>();

    @OneToMany(mappedBy = "court", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AvailabilityEntity> availabilities = new HashSet<>();

    @OneToMany(mappedBy = "court", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ReservationEntity> reservations = new HashSet<>();

}
