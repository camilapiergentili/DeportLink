package com.deportlink.DeportLink.model.entity;

import com.deportlink.DeportLink.model.CompanyType;
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
    private String legalName;

    @Enumerated(EnumType.STRING)
    private CompanyType companyType;

    private String cuit;
    private boolean verified;

    @ManyToMany(mappedBy = "clubs")
    private Set<OwnerEntity> owners = new HashSet<>();

    @OneToMany(mappedBy = "clubs", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BranchEntity> branches = new HashSet<>();
}
