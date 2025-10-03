package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.response.OwnerResponseDto;
import com.deportlink.DeportLink.model.entity.OwnerEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {ClubMapper.class})
public interface OwnerMapper extends UserMapper {

    OwnerResponseDto toResponse(OwnerEntity ownerEntity);
}
