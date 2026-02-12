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
    @Mapping(source = "court.name", target = "nameCourt")
    @Mapping(source = "court.sport.nameSport", target = "sport")
    @Mapping(source = "player", target = "namePlayer", qualifiedByName = "fullName")
    @Mapping(target = "totalPrice", expression = "java(reservationEntity.getTotalPrice())")
    @Mapping(source = "court.branch.address", target = "address", qualifiedByName = "address")
    ReservationResponseDto toResponse(ReservationEntity reservationEntity);

    @Mapping(source = "day", target = "day", qualifiedByName = "stringToLocalDate")
    @Mapping(source = "startTime",target = "startTime", qualifiedByName = "stringToLocalTime")
    @Mapping(source = "durationMinutes", target = "duration", qualifiedByName = "minutesToDuration")
    ReservationEntity toModel(ReservationRequestDto reservationRequestDto);

    @Named("durationToMinutes")
    static Long durationToMinutes(Duration duration){
        return duration == null ? null : duration.toMinutes();
    }

    @Named("minutesToDuration")
    static Duration minutesToDuration(Long min){
        return min == null ? null : Duration.ofMinutes(min);
    }

    @Named("localDateToString")
    static String localDateToString(LocalDate localDate){
        return localDate == null ? null : localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    @Named("stringToLocalDate")
    static LocalDate stringToLocalDate(String date){
        return date == null ? null : LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    @Named("localTimeToString")
    static String localTimeToString(LocalTime localTime){
        return localTime == null ? null : localTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    @Named("stringToLocalTime")
    static LocalTime stringToLocalTime(String time){
        return time == null ? null : LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
    }

    @Named("fullName")
    static String fullName(PlayerEntity player){
        if(player == null) return null;
        return player.getFirstName() + " " + player.getLastName();
    }

    @Named("address")
    static String address(AddressEntity addressEntity){
        if(addressEntity == null) return null;
        return addressEntity.getStreetName() + " " + addressEntity.getNumber();
    }
}
