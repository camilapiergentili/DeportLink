package com.deportlink.deportlink.mapper;

import com.deportlink.deportlink.dto.request.ReservationRequestDto;
import com.deportlink.deportlink.dto.response.ReservationResponseDto;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    @Mapping(source = "duration", target = "duration", qualifiedByName = "durationToMinutes")
    @Mapping(source = "day", target = "day", qualifiedByName = "localDateToString")
    @Mapping(source = "startTime", target = "startTime", qualifiedByName = "localTimeToString")
    ReservationResponseDto toResponse(ReservationEntity reservationEntity);


    @Mapping(target = "court", ignore = true)
    @Mapping(target = "player", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "duration", ignore = true)
    @Mapping(target = "ticket", ignore = true)
    ReservationEntity toModel(ReservationRequestDto reservationRequestDto);

    @Named("durationToMinutes")
    static Long durationToMinutes(Duration duration){
        return duration == null ? null : duration.toMinutes();
    }

    @Named("localDateToString")
    static String localDateToString(LocalDate localDate){
        return localDate == null ? null : localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    @Named("localTimeToString")
    static String localTimeToString(LocalTime localTime){
        return localTime == null ? null : localTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

}
