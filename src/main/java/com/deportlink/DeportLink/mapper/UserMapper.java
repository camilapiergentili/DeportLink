package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.request.UserRequestDto;
import com.deportlink.DeportLink.dto.response.UserResponseDto;
import com.deportlink.DeportLink.model.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // Dto -> Entity
    UserEntity toModel(UserRequestDto userDto);

    // Entity -> Response
    UserResponseDto toResponse(UserEntity userEntity);

}
