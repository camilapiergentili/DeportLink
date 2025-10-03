package com.deportlink.DeportLink.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "clubs")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ClubEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String name;

    @ManyToMany(mappedBy = "clubs")
    private Set<OwnerEntity> owners = new HashSet<>();

    @OneToMany(mappedBy = "clubs", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BranchEntity> branches = new HashSet<>();
}
