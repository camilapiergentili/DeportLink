package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.model.ActiveStatus;
import com.deportlink.deportlink.model.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "branches")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BranchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String name;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "address_id", referencedColumnName = "id")
    private AddressEntity address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private ClubEntity club;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    private ActiveStatus activeStatus;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourtEntity> courts = new ArrayList<>();

}
