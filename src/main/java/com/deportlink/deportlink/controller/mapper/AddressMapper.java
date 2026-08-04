package com.deportlink.deportlink.controller.mapper;

import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.PlayerAddress;
import com.deportlink.deportlink.dto.request.AddressRequestDto;
import com.deportlink.deportlink.dto.response.AddressResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AddressMapper {

    AddressResponseDto toResponse(Address address);

    // PlayerAddress tiene isDefault que no existe en el DTO — queda como unmapped source (IGNORED)
    AddressResponseDto toResponse(PlayerAddress address);

    Address toDomain(AddressRequestDto dto);
}