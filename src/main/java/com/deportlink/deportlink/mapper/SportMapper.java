package com.deportlink.deportlink.mapper;

import com.deportlink.deportlink.dto.request.SportRequestDto;
import com.deportlink.deportlink.dto.response.SportResponseDto;
import com.deportlink.deportlink.model.entity.SportEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SportMapper {

    SportEntity toModel(SportRequestDto sportRequestDto);

    SportResponseDto toResponse(SportEntity sportEntity);

}
