package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.response.TicketResponseDto;
import com.deportlink.deportlink.mapper.TicketMapper;
import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.TicketRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class TicketServiceImplementation {

    private final TicketMapper ticketMapper;
    private final TicketRepository ticketRepository;

    public TicketResponseDto generateTicket(ReservationEntity reservation, Double totalPrice) {
        TicketEntity ticket = buildTicket(reservation, totalPrice);
        ticketRepository.save(ticket);
        return ticketMapper.toResponse(ticket);
    }

    private TicketEntity buildTicket(ReservationEntity reservation, Double totalPrice) {
        PlayerEntity player = reservation.getPlayer();
        CourtEntity court = reservation.getCourt();
        BranchEntity branch = court.getBranch();

        TicketEntity ticket = new TicketEntity();
        ticket.setReservation(reservation);
        ticket.setPlayerName(player.getFirstName());
        ticket.setPlayerLastName(player.getLastName());
        ticket.setCourtName(court.getName());
        ticket.setSport(court.getSport().getNameSport());
        ticket.setBranchName(branch.getName());
        ticket.setBranchAddress(branch.getAddress().getStreetName());
        ticket.setTotalPrice(totalPrice);
        ticket.setIssuedAt(LocalDateTime.now());
        return ticket;
    }
}

