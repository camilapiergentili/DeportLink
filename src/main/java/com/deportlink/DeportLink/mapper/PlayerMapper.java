package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.request.AddressRequestDto;
import com.deportlink.DeportLink.dto.request.PlayerRequestDto;
import com.deportlink.DeportLink.model.entity.AddressEntity;
import com.deportlink.DeportLink.model.entity.PlayerEntity;
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
    PlayerEntity toModel(PlayerRequestDto playerRequestDto);

    default Set<AddressEntity> toAddressSet(AddressRequestDto dto){
        if(dto == null) return new HashSet<>();

        Set<AddressEntity> set = new HashSet<>();
        set.add(toModel(dto));
        return set;
    }

    AddressEntity toModel(AddressRequestDto dto);
}
