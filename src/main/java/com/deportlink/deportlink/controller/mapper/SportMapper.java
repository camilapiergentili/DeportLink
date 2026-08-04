package com.deportlink.deportlink.controller.mapper;

import com.deportlink.deportlink.domain.model.Sport;
import com.deportlink.deportlink.dto.response.SportResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SportMapper {

    @Mapping(source = "name", target = "nameSport")
    SportResponseDto toResponse(Sport sport);
}