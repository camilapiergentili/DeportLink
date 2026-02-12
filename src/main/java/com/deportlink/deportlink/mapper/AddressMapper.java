package com.deportlink.deportlink.mapper;

import com.deportlink.deportlink.dto.request.AddressRequestDto;
import com.deportlink.deportlink.dto.response.AddressResponseDto;
import com.deportlink.deportlink.model.entity.AddressEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    AddressEntity toModel(AddressRequestDto addressRequestDto);

    AddressResponseDto toResponse(AddressEntity addressEntity);
}
