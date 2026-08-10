package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.exception.ReservationNotUpdateException;
import com.deportlink.deportlink.enums.StatusReservation;
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
@Table(
    name = "reservation",
    indexes = {
        // Cubre findActiveByCourtAndDay (verificación de disponibilidad + pessimistic lock)
        @Index(name = "idx_reservation_court_day_status", columnList = "court_id, reservation_day, status"),
        // Cubre findActiveByCourt (historial de reservas por cancha)
        @Index(name = "idx_reservation_court_status", columnList = "court_id, status"),
        // Cubre getByPlayer (historial del jugador)
        @Index(name = "idx_reservation_player", columnList = "player_id")
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_day")
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

    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL)
    private TicketEntity ticket;

    public void cancel() {

        if(this.status == StatusReservation.CANCELADO ||
                this.status == StatusReservation.FINALIZADO){

            throw new ReservationNotUpdateException(
                    "La reserva no puede cancelarse"
            );
        }

        this.status = StatusReservation.CANCELADO;
    }

}
