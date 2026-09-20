package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.persistence.converter.DurationConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

/**
 * Plantilla/configuración recurrente de una clase — "todos los jueves a las 15:00".
 * Ver docs/class-management-stage-1c-persistence-design.md, sección 3.2.
 */
@Entity
@Table(
    name = "class_slot",
    indexes = {
        @Index(name = "idx_class_slot_instructor", columnList = "instructor_id"),
        @Index(name = "idx_class_slot_court_day", columnList = "court_id, day_of_week")
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClassSlotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id", nullable = false)
    private InstructorEntity instructor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    private CourtEntity court;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration", nullable = false)
    @Convert(converter = DurationConverter.class)
    private Duration duration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_status", nullable = false)
    private ActiveStatus activeStatus;
}
