package com.deportlink.deportlink.mapper.dto;

import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.dto.response.ScheduleResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ScheduleMapper {

    @Mapping(target = "slotDuration", expression = "java(schedule.slotDuration().toMinutes())")
    ScheduleResponseDto toResponse(Schedule schedule);
}