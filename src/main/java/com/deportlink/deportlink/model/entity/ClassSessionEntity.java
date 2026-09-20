package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.enums.ClassSessionStatus;
import com.deportlink.deportlink.persistence.converter.DurationConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Ocurrencia concreta de una fecha para un ClassSlot. Deliberadamente SIN court_id — decisión
 * cerrada, ver docs/class-management-stage-1c-persistence-design.md, sección 3.4: la cancha se
 * resuelve siempre a través de ClassSlot; la disponibilidad compartida se consulta contra
 * court_occupancy, que ya tiene su propio court_id. Mismo contrato que ClassSession (dominio, 1A/1B).
 */
@Entity
@Table(
    name = "class_session",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_class_session_slot_date", columnNames = {"class_slot_id", "session_date"})
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClassSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_slot_id", nullable = false)
    private ClassSlotEntity classSlot;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration", nullable = false)
    @Convert(converter = DurationConverter.class)
    private Duration duration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassSessionStatus status;
}
