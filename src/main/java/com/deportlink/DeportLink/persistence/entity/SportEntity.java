package com.deportlink.DeportLink.persistence.entity;

import com.deportlink.DeportLink.model.Court;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name= "sport")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String nameSport;

    @OneToMany(mappedBy = "sport", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CourtEntity> courts = new HashSet<>();
}
