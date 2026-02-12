package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.model.StatusReservation;
import com.deportlink.deportlink.persistence.converter.DurationConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name= "reservation")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private LocalDate day;
    private LocalTime startTime;

    @Column(name = "duration")
    @Convert(converter = DurationConverter.class)
    private Duration duration;

    @Enumerated(EnumType.STRING)
    private StatusReservation status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id")
    private CourtEntity court;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private PlayerEntity player;

    public LocalTime getEndTime() {
        return (startTime == null || duration == null) ?
                null : startTime.plusMinutes(duration.toMinutes());
    }

    public Double getTotalPrice(){
        return (duration == null || court.getPricePerHour() < 0.0) ?
                0.00 : (duration.toMinutes() / 60.0) * court.getPricePerHour();
    }
}
