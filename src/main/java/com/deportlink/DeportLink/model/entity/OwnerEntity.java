package com.deportlink.DeportLink.model.entity;

import com.deportlink.DeportLink.model.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "owners")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OwnerEntity extends UserEntity {

    private String dni;
    private String cuil;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "owner_club",
            joinColumns = @JoinColumn(name = "owner_id"),
            inverseJoinColumns = @JoinColumn(name = "club_id")
    )
    private List<ClubEntity> clubs = new ArrayList<>();
}
