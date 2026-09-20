package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "branches",
    indexes = {
        // Cubre findActiveAndApprovedByClubId — extiende el índice FK de club_id con los filtros de estado
        @Index(name = "idx_branches_club_verification_active", columnList = "club_id, verification_status, active_status")
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BranchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "address_id")
    private AddressEntity address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private ClubEntity club;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    private ActiveStatus activeStatus;

    @Column(nullable = false)
    private int cancellationWindowHours;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourtEntity> courts = new ArrayList<>();

}
