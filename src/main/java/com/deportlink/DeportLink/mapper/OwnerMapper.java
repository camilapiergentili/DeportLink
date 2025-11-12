package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.request.OwnerRequestDto;
import com.deportlink.DeportLink.dto.response.OwnerResponseDto;
import com.deportlink.DeportLink.model.entity.OwnerEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring", uses = {ClubMapper.class})
public interface OwnerMapper extends UserMapper {

    @Mapping(source = "dateOfBirth", target = "dateOfBirth", qualifiedByName = "localDateToString")
    @Mapping(target = "role", expression = "java(com.deportlink.DeportLink.model.Rol.OWNER)")
    OwnerEntity toModel(OwnerRequestDto ownerRequestDto);

    @Mapping(source = "dateOfBirth", target = "dateOfBirth", qualifiedByName = "stringToLocalDate")
    OwnerResponseDto toResponse(OwnerEntity ownerEntity);

    @Named("localDateToString")
    static String localDateToString(LocalDate localDate){
        return localDate == null ? null : localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    @Named("stringToLocalDate")
    static LocalDate stringToLocalDate(String date){
        return date == null ? null : LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
