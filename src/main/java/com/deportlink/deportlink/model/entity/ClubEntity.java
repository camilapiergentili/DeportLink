package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.model.ActiveStatus;
import com.deportlink.deportlink.model.ClubType;
import com.deportlink.deportlink.model.VerificationStatus;
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
    private String cuit;

    @Enumerated(EnumType.STRING)
    private ClubType clubType;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    private ActiveStatus activeStatus;

    @ManyToMany(mappedBy = "clubs")
    private Set<OwnerEntity> owners = new HashSet<>();

    @OneToMany(mappedBy = "club", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BranchEntity> branches = new HashSet<>();
}
