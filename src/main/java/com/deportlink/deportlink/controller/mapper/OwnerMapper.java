package com.deportlink.deportlink.controller.mapper;

import com.deportlink.deportlink.domain.model.Owner;
import com.deportlink.deportlink.dto.response.OwnerResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OwnerMapper {

    @Mapping(target = "role", ignore = true)          // Owner domain no tiene rol
    @Mapping(target = "clubs", ignore = true)          // clubs se carga bajo demanda
    @Mapping(target = "dni", expression = "java(String.valueOf(owner.dni()))")
    @Mapping(target = "dateOfBirth", source = "dateOfBirth", qualifiedByName = "localDateToString")
    OwnerResponseDto toResponse(Owner owner);

    @Named("localDateToString")
    default String localDateToString(LocalDate date) {
        return date != null ? date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : null;
    }
}