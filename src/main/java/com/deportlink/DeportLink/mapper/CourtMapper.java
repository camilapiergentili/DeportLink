package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.request.CourtRequestDto;
import com.deportlink.DeportLink.dto.response.CourtResponseDto;
import com.deportlink.DeportLink.model.entity.CourtEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {BranchMapper.class, SportMapper.class, AvailabilityMapper.class})
public interface CourtMapper {

    CourtEntity toModel(CourtRequestDto dto);

    @Mapping(source = "entity.sport.nameSport", target = "sport")
    CourtResponseDto toResponse(CourtEntity entity);

}
