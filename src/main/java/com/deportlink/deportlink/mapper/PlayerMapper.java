package com.deportlink.deportlink.mapper;

import com.deportlink.deportlink.dto.request.AddressRequestDto;
import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.Set;

@Mapper(componentModel = "spring")
public abstract class PlayerMapper {

    // Spring los inyecta automáticamente
    @Autowired
    protected AddressMapper addressMapper;

    @Mapping(
            target = "addresses",
            expression = "java(toAddressSet(playerRequestDto.getAddressRequestDto()))"
    )
    @Mapping(
            target = "role",
            expression = "java(com.deportlink.deportlink.model.Rol.PLAYER)"
    )
    @Mapping(target = "password", ignore = true)
    public abstract PlayerEntity toModel(PlayerRequestDto playerRequestDto);

    public abstract PlayerResponseDto toResponse(PlayerEntity playerEntity);

    protected Set<AddressEntity> toAddressSet(AddressRequestDto dto){
        if(dto == null) return new HashSet<>();

        Set<AddressEntity> set = new HashSet<>();
        set.add(addressMapper.toModel(dto));
        return set;
    }
}
