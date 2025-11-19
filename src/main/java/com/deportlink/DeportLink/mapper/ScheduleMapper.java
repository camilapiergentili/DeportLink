package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.request.ScheduleRequestDto;
import com.deportlink.DeportLink.dto.response.ScheduleResponseDto;
import com.deportlink.DeportLink.model.entity.ScheduleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ScheduleMapper {

    @Mapping(source = "openingTime", target = "openingTime", qualifiedByName = "stringToLocalTime")
    @Mapping(source = "endingTime", target = "endingTime", qualifiedByName = "stringToLocalTime")
    @Mapping(source = "day", target = "day", qualifiedByName = "stringToDayOfWeek")
    ScheduleEntity toModel(ScheduleRequestDto dto);


    @Mapping(source = "openingTime", target = "openingTime", qualifiedByName = "localTimeToString")
    @Mapping(source = "endingTime", target = "endingTime", qualifiedByName = "localTimeToString")
    @Mapping(source = "day", target = "day", qualifiedByName = "dayOfWeekToString")
    ScheduleResponseDto toResponse(ScheduleEntity entity);

    List<ScheduleEntity> toModelList(List<ScheduleRequestDto> dtoList);


    @Named("stringToDayOfWeek")
    static DayOfWeek stringToDayOfWeek(String day){
        return day == null ? null : DayOfWeek.valueOf(day.toUpperCase());
    }

    @Named("dayOfWeekToString")
    static String dayOfWeekToString(DayOfWeek day){
        return day == null ? null : day.name().toLowerCase();
    }

    @Named("stringToLocalTime")
    static LocalTime stringToLocalTime(String time){
        return time == null ? null : LocalTime.parse(time, DateTimeFormatter.ofPattern("H:mm"));
    }

    @Named("localTimeToString")
    static String localTimeToString(LocalTime time){
        return time == null ? null : time.format(DateTimeFormatter.ofPattern("H:mm"));
    }
}
