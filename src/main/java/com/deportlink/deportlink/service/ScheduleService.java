package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.ScheduleRequestDto;
import com.deportlink.deportlink.dto.response.ScheduleResponseDto;
import com.deportlink.deportlink.model.entity.ScheduleEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ScheduleService {
    void addSchedule(long idCourt, List<ScheduleRequestDto> schedulesDto);
    void deleteSchedule(long idSchedule, long idCourt);
    void updateSchedule(long idSchedule, long idCourt, String openingNew, String closingNew);
    List<ScheduleResponseDto> getAllByCourt(long idCourt);
    ScheduleResponseDto getByDay(long idCourt, LocalDate day);
    ScheduleEntity getByDay(long idCourt, DayOfWeek dayOfWeek);
    ScheduleEntity getAndValidateSchedule(long idCourt, LocalDate day, LocalTime time);


}
