package com.deportlink.deportlink.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pertenencia de un Player al grupo fijo de un ClassSlot. UNIQUE(class_slot_id, player_id) sin
 * filtrar por active — un alumno tiene una única fila por horario, esté activa o no (ver
 * docs/class-management-stage-1c-persistence-design.md, sección 3.3).
 */
@Entity
@Table(
    name = "class_enrollment",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_class_enrollment_slot_player", columnNames = {"class_slot_id", "player_id"})
    },
    indexes = {
        @Index(name = "idx_class_enrollment_player", columnList = "player_id")
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClassEnrollmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_slot_id", nullable = false)
    private ClassSlotEntity classSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;

    @Column(nullable = false)
    private boolean active;
}
