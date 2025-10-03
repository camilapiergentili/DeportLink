package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.request.AddressRequestDto;
import com.deportlink.DeportLink.dto.response.AddressResponseDto;
import com.deportlink.DeportLink.model.entity.AddressEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {PlayerMapper.class})
public interface AddressMapper {

    AddressEntity toModel(AddressRequestDto addressRequestDto);

    AddressResponseDto toResponse(AddressEntity addressEntity);
}
