package com.deportlink.deportlink.mapper;

import com.deportlink.deportlink.dto.response.TicketResponseDto;
import com.deportlink.deportlink.model.entity.TicketEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    @Mapping(target = "player", expression = "java(ticket.getPlayerName() + ' ' + ticket.getPlayerLastName())")
    @Mapping(target = "issuedAt", expression = "java(ticket.getIssuedAt().toString())")
    TicketResponseDto toResponse(TicketEntity ticket);
}
