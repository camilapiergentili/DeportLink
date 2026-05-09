package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.persistence.converter.DurationConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "tickets")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "reservation_id")
    private ReservationEntity reservation;

    private String playerName;
    private String playerLastName;

    private String courtName;
    private String sport;

    private String branchName;
    private String branchAddress;

    private Double totalPrice;

    // Fecha en que se emitió el ticket
    private LocalDateTime issuedAt;
}
