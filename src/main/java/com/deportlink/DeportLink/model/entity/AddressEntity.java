package com.deportlink.DeportLink.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


@Entity
@Table(name = "address")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class
AddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String streetName;
    private int number;
    private String city;
    private String province;
    private int postalCode;
    private double latitude;
    private double longitude;

    @ManyToMany(mappedBy = "addresses")
    Set<PlayerEntity> players = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AddressEntity that)) return false;
        return number == that.number &&
                postalCode == that.postalCode &&
                Double.compare(that.latitude, latitude) == 0 &&
                Double.compare(that.longitude, longitude) == 0 &&
                Objects.equals(streetName, that.streetName) &&
                Objects.equals(city, that.city) &&
                Objects.equals(province, that.province);
    }

    @Override
    public int hashCode() {
        return Objects.hash(streetName, number, city, province, postalCode, latitude, longitude);
    }

}
