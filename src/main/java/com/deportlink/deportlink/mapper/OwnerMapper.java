package com.deportlink.deportlink.mapper;

import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.OwnerResponseDto;
import com.deportlink.deportlink.model.entity.OwnerEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring", uses = {ClubMapper.class})
public interface OwnerMapper extends UserMapper {

    @Mapping(source = "dateOfBirth", target = "dateOfBirth", qualifiedByName = "stringToLocalDate")
    @Mapping(target = "role", expression = "java(com.deportlink.deportlink.model.Rol.OWNER)")
    OwnerEntity toModel(OwnerRequestDto ownerRequestDto);

    @Mapping(source = "dateOfBirth", target = "dateOfBirth", qualifiedByName = "localDateToString")
    OwnerResponseDto toResponse(OwnerEntity ownerEntity);

    @Named("localDateToString")
    default String localDateToString(LocalDate localDate){
        return localDate == null ? null :
                localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    @Named("stringToLocalDate")
    default LocalDate stringToLocalDate(String date){
        return date == null ? null : LocalDate.parse(date,
                DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}


