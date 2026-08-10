package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.model.Ticket;
import com.deportlink.deportlink.domain.model.TimeSlot;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.exception.ReservationNotFoundException;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import com.deportlink.deportlink.model.entity.TicketEntity;
import com.deportlink.deportlink.persistence.repository.CourtRepository;
import com.deportlink.deportlink.persistence.repository.PlayerRepository;
import com.deportlink.deportlink.persistence.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter de infraestructura: traduce entre el modelo de dominio (records inmutables)
 * y el modelo de persistencia (entidades JPA mutables con relaciones lazy).
 * <p>
 * Patrón Adapter (GoF): permite que el dominio use ReservationRepositoryPort
 * sin saber nada de JPA, Spring Data, ni ReservationEntity.
 */
@Component
@RequiredArgsConstructor
public class ReservationRepositoryAdapter implements ReservationRepositoryPort {

    private final ReservationRepository reservationRepository;
    private final CourtRepository courtRepository;
    private final PlayerRepository playerRepository;

    @Override
    public Reservation save(Reservation domain) {
        ReservationEntity entity = domain.id() == null
                ? buildNewEntity(domain)
                : updateExistingEntity(domain);
        return toDomain(reservationRepository.save(entity));
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        return reservationRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Set<LocalTime> findBookedSlots(Long courtId, LocalDate day) {
        // Deriva dinámicamente los estados que bloquean slot — si el enum crece, esta línea no cambia.
        List<StatusReservation> occupying = Arrays.stream(StatusReservation.values())
                .filter(StatusReservation::occupiesSlot)
                .toList();
        return reservationRepository.findStartTimesByCourtAndDay(courtId, day, occupying);
    }

    @Override
    public List<Reservation> findByPlayerId(Long playerId) {
        return reservationRepository.findByPlayer_Id(playerId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ActiveSlot> findActiveByCourtAndDay(Long courtId, DayOfWeek day) {
        List<StatusReservation> occupying = Arrays.stream(StatusReservation.values())
                .filter(StatusReservation::occupiesSlot)
                .toList();
        return reservationRepository.findActiveByCourt(courtId, occupying).stream()
                .filter(r -> r.getDay().getDayOfWeek() == day)
                .map(r -> new ActiveSlot(r.getStartTime(), r.getDuration()))
                .toList();
    }

    // ─── Entity builders ────────────────────────────────────────────────────────

    private ReservationEntity buildNewEntity(Reservation domain) {
        ReservationEntity entity = new ReservationEntity();
        entity.setDay(domain.timeSlot().day());
        entity.setStartTime(domain.timeSlot().startTime());
        entity.setDuration(domain.timeSlot().duration());
        entity.setStatus(domain.status());

        // getReferenceById crea un proxy con solo el FK — no dispara un SELECT.
        // Si el ID no existe, el error surge en el INSERT (FK violation), no aquí.
        // Eso está bien: la validación de existencia ya ocurrió en el caso de uso.
        entity.setCourt(courtRepository.getReferenceById(domain.courtId()));
        entity.setPlayer(playerRepository.getReferenceById(domain.playerId()));

        if (domain.ticket() != null) {
            TicketEntity ticketEntity = toTicketEntity(domain.ticket());
            ticketEntity.setReservation(entity);
            entity.setTicket(ticketEntity);
        }
        return entity;
    }

    private ReservationEntity updateExistingEntity(Reservation domain) {
        // Para updates solo mutamos el status — timeSlot, court y player son inmutables post-creación.
        // Alternativa descartada: crear una nueva entidad con el mismo id y hacer merge.
        // Problema: tendríamos que re-proveer court, player y ticket aunque no cambiaron,
        // lo que acopla este método a conocer qué campos pueden cambiar.
        ReservationEntity entity = reservationRepository.findById(domain.id())
                .orElseThrow(() -> new ReservationNotFoundException("Reserva no encontrada para actualizar"));
        entity.setStatus(domain.status());
        return entity;
    }

    // ─── Domain ↔ Entity mapping ────────────────────────────────────────────────

    private Reservation toDomain(ReservationEntity entity) {
        TimeSlot timeSlot = new TimeSlot(entity.getDay(), entity.getStartTime(), entity.getDuration());
        Ticket ticket = entity.getTicket() != null ? toTicketDomain(entity.getTicket()) : null;

        // getCourt().getId() / getPlayer().getId() no disparan lazy load:
        // Hibernate resuelve el id directamente del proxy sin ir a la BD.
        return new Reservation(
                entity.getId(),
                entity.getCourt().getId(),
                entity.getPlayer().getId(),
                timeSlot,
                entity.getStatus(),
                ticket
        );
    }

    private TicketEntity toTicketEntity(Ticket ticket) {
        TicketEntity entity = new TicketEntity();
        entity.setPlayerName(ticket.playerName());
        entity.setPlayerLastName(ticket.playerLastName());
        entity.setCourtName(ticket.courtName());
        entity.setSport(ticket.sport());
        entity.setBranchName(ticket.branchName());
        entity.setBranchAddress(ticket.branchAddress());
        entity.setTotalPrice(ticket.totalPrice());
        entity.setIssuedAt(ticket.issuedAt());
        return entity;
    }

    private Ticket toTicketDomain(TicketEntity entity) {
        return new Ticket(
                entity.getId(),
                entity.getPlayerName(),
                entity.getPlayerLastName(),
                entity.getCourtName(),
                entity.getSport(),
                entity.getBranchName(),
                entity.getBranchAddress(),
                entity.getTotalPrice(),
                entity.getIssuedAt()
        );
    }
}