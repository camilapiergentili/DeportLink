package com.deportlink.deportlink.mapper;

import com.deportlink.deportlink.dto.request.AddressRequestDto;
import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.HashSet;
import java.util.Set;

@Mapper(componentModel = "spring", uses = {AddressMapper.class, ReservationMapper.class})
public interface PlayerMapper extends UserMapper {

    @Mapping(
            target = "addresses",
            expression = "java(toAddressSet(playerRequestDto.getAddressRequestDto()))"
    )
    @Mapping(target = "role", expression = "java(com.deportlink.deportlink.model.Rol.PLAYER)")
    PlayerEntity toModel(PlayerRequestDto playerRequestDto);

    PlayerResponseDto toResponse(PlayerEntity playerEntity);

    default Set<AddressEntity> toAddressSet(AddressRequestDto dto){
        if(dto == null) return new HashSet<>();

        Set<AddressEntity> set = new HashSet<>();
        AddressMapper addressMapper = new AddressMapperImpl();
        set.add(addressMapper.toModel(dto));
        return set;
    }

}
