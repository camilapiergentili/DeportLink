package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.request.OwnerRequestDto;
import com.deportlink.DeportLink.dto.response.OwnerResponseDto;
import com.deportlink.DeportLink.model.entity.OwnerEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {ClubMapper.class})
public interface OwnerMapper extends UserMapper {

    OwnerEntity toModel(OwnerRequestDto ownerRequestDto);

    OwnerResponseDto toResponse(OwnerEntity ownerEntity);
}
