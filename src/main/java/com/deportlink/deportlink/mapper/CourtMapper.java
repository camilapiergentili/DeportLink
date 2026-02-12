package com.deportlink.deportlink.mapper;

import com.deportlink.deportlink.dto.request.CourtRequestDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.model.entity.CourtEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {BranchMapper.class, SportMapper.class, ScheduleMapper.class})
public interface CourtMapper {

    CourtEntity toModel(CourtRequestDto dto);

    @Mapping(source = "entity.sport.nameSport", target = "sport")
    CourtResponseDto toResponse(CourtEntity entity);

}
