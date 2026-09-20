package com.deportlink.deportlink.model.entity;


import com.deportlink.deportlink.enums.ActiveStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;


@Entity
@Table(
    name = "court",
    indexes = {
        // Cubre findByBranch_IdAndActiveStatus (listado paginado de canchas activas por sede)
        @Index(name = "idx_court_branch_active", columnList = "branch_id, active_status"),
        // Cubre findByBranch_IdAndSport_Id (filtrado por deporte dentro de una sede)
        @Index(name = "idx_court_branch_sport", columnList = "branch_id, sport_id")
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CourtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private double pricePerHour;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private BranchEntity branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sport_id")
    private SportEntity sport;

    @Enumerated(EnumType.STRING)
    private ActiveStatus activeStatus;

    @OneToMany(mappedBy = "court", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ScheduleEntity> schedules = new HashSet<>();

    // Sin cascade ni orphanRemoval: Reservation representa historial y no debe
    // eliminarse como efecto secundario de borrar una Court. DeleteCourtUseCase
    // además bloquea el borrado si existe alguna reserva (ver hasReservations());
    // esto es la segunda línea de defensa — si ese guard se saltara alguna vez,
    // el DELETE de la cancha chocaría con la FK fk_reservation_court (RESTRICT)
    // en vez de arrastrar las reservas silenciosamente.
    @OneToMany(mappedBy = "court")
    private Set<ReservationEntity> reservations = new HashSet<>();

}
