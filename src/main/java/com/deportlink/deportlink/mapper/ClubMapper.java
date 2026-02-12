package com.deportlink.deportlink.mapper;

import com.deportlink.deportlink.dto.request.ClubRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.model.entity.ClubEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {BranchMapper.class})
public interface ClubMapper {

    ClubEntity toModel(ClubRequestDto dto);

    ClubResponseDto toResponse(ClubEntity entity);

}
