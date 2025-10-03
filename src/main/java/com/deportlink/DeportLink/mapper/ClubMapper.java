package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.request.ClubRequestDto;
import com.deportlink.DeportLink.dto.response.ClubResponseDto;
import com.deportlink.DeportLink.model.entity.BranchEntity;
import com.deportlink.DeportLink.model.entity.ClubEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {BranchMapper.class})
public interface ClubMapper {

    ClubEntity toModel(ClubRequestDto dto);

    ClubResponseDto toResponse(ClubEntity entity);

}
