package com.deportlink.deportlink.mapper.dto;

import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CourtMapper {

    @Mapping(source = "sportName", target = "sport")
    // availabilities se carga bajo demanda — no existe en el agregado Court
    @Mapping(target = "availabilities", ignore = true)
    CourtResponseDto toResponse(Court court);
}