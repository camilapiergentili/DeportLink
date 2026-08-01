package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.persistence.converter.DurationConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

@Entity
@Table(
    name = "availability",
    indexes = {
        // Cubre findByCourtIdAndDay — llamado en cada generación de turnos disponibles
        @Index(name = "idx_availability_court_day", columnList = "court_id, day_of_week")
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private DayOfWeek day;

    private LocalTime openingTime;
    private LocalTime closingTime;

    @Column(name = "slotDuration")
    @Convert(converter = DurationConverter.class)
    private Duration slotDuration;

    @ManyToOne
    @JoinColumn(name = "court_id")
    private CourtEntity court;

}
