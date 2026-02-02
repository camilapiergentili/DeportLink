package com.deportlink.DeportLink.model.entity;

import com.deportlink.DeportLink.persistence.converter.DurationConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

@Entity
@Table(name= "availability")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Enumerated(EnumType.STRING)
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
