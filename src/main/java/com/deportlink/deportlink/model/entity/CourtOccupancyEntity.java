package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.enums.OccupancySourceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Garantía compartida "una Court no puede estar ocupada por una Reservation y una ClassSession al
 * mismo tiempo" — ver docs/class-management-stage-1c-persistence-design.md, sección 3.6/6.
 * source_type + source_id identifican el origen sin ambigüedad, para poder liberar por origen
 * (nunca por coordenadas) sin borrar la fila de otro.
 */
@Entity
@Table(
    name = "court_occupancy",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_court_occupancy_slot", columnNames = {"court_id", "occupied_day", "start_time"})
    },
    indexes = {
        @Index(name = "idx_court_occupancy_source", columnList = "source_type, source_id")
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CourtOccupancyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    private CourtEntity court;

    @Column(name = "occupied_day", nullable = false)
    private LocalDate occupiedDay;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private OccupancySourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;
}
