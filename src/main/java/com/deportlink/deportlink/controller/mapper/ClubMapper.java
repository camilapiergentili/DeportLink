package com.deportlink.deportlink.controller.mapper;

import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClubMapper {

    // branches se carga bajo demanda — no existe en el agregado Club
    @Mapping(target = "branches", ignore = true)
    ClubResponseDto toResponse(Club club);
}