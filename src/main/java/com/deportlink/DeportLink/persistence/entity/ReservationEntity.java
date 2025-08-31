package com.deportlink.DeportLink.persistence.entity;

import com.deportlink.DeportLink.model.Court;
import com.deportlink.DeportLink.model.StatusReservation;
import com.deportlink.DeportLink.model.User;
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
    private LocalTime endTime;
    private Duration duration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id")
    private CourtEntity court;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private UserEntity player;

    @Enumerated(EnumType.STRING)
    private StatusReservation status;

}
