package com.deportlink.deportlink.mapper.dto;

import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.model.Ticket;
import com.deportlink.deportlink.dto.response.ReservationResponseDto;
import com.deportlink.deportlink.dto.response.TicketResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReservationMapper {

    @Mapping(source = "timeSlot.day", target = "day")
    @Mapping(source = "timeSlot.startTime", target = "startTime")
    @Mapping(target = "durationMinutes", expression = "java(reservation.timeSlot().duration().toMinutes())")
    ReservationResponseDto toResponse(Reservation reservation);

    // Default method: campos con lógica no declarativa (concatenación de nombre, null-safety en id primitivo)
    default TicketResponseDto toResponse(Ticket ticket) {
        if (ticket == null) return null;
        TicketResponseDto dto = new TicketResponseDto();
        dto.setId(ticket.id() != null ? ticket.id() : 0L);
        dto.setPlayer(ticket.playerName() + " " + ticket.playerLastName());
        dto.setCourtName(ticket.courtName());
        dto.setSport(ticket.sport());
        dto.setBranchName(ticket.branchName());
        dto.setBranchAddress(ticket.branchAddress());
        dto.setTotalPrice(ticket.totalPrice());
        dto.setIssuedAt(ticket.issuedAt() != null ? ticket.issuedAt().toString() : null);
        return dto;
    }
}