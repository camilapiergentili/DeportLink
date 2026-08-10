package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.ClubType;
import com.deportlink.deportlink.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
    name = "clubs",
    uniqueConstraints = {
        // Garantía a nivel DB — complementa la validación en el service
        @UniqueConstraint(name = "uq_clubs_cuit", columnNames = "cuit"),
        @UniqueConstraint(name = "uq_clubs_legal_name", columnNames = "legal_name")
    },
    indexes = {
        // Cubre todas las queries de listado: findApprovedWithEagerLoading, findApprovedPaginated
        @Index(name = "idx_clubs_verification_active", columnList = "verification_status, active_status")
    }
)
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ClubEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
