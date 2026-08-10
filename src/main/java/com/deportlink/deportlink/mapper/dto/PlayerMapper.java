package com.deportlink.deportlink.mapper.dto;

import com.deportlink.deportlink.domain.model.Player;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, uses = {AddressMapper.class})
public interface PlayerMapper {

    @Mapping(target = "role", ignore = true)  // Player domain no tiene rol
    PlayerResponseDto toResponse(Player player);
}