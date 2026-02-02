package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.response.AppointmentResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalTime;
import java.util.List;

@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(source = "times", target = "appointmentsAvailable")
    AppointmentResponseDto toResponse(List<LocalTime> times);

}