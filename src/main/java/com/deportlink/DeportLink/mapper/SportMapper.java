package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.request.SportRequestDto;
import com.deportlink.DeportLink.dto.response.SportResponseDto;
import com.deportlink.DeportLink.model.entity.SportEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SportMapper {

    SportEntity toModel(SportRequestDto sportRequestDto);

    SportResponseDto toResponse(SportEntity sportEntity);

}
