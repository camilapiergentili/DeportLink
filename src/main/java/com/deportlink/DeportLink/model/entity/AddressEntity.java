package com.deportlink.DeportLink.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "address")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String streetName;
    private int number;
    private String city;
    private String province;
    private int code;
    private double latitude;
    private double longitude;


    @ManyToMany(mappedBy = "addresses")
    Set<PlayerEntity> players = new HashSet<>();

}
