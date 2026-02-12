package com.deportlink.deportlink.mapper;

import com.deportlink.deportlink.dto.request.UserRequestDto;
import com.deportlink.deportlink.dto.response.UserResponseDto;
import com.deportlink.deportlink.model.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // Dto -> Entity
    UserEntity toModel(UserRequestDto userDto);

    // Entity -> Response
    UserResponseDto toResponse(UserEntity userEntity);

}
