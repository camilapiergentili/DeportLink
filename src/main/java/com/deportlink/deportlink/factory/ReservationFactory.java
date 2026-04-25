package com.deportlink.deportlink.factory;

import com.deportlink.deportlink.dto.request.ReservationRequestDto;
import com.deportlink.deportlink.mapper.ReservationMapper;
import com.deportlink.deportlink.model.StatusReservation;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import com.deportlink.deportlink.model.entity.ScheduleEntity;
import com.deportlink.deportlink.service.ScheduleService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class ReservationFactory {

    private final ReservationMapper reservationMapper;
    private final ScheduleService scheduleService;

    public ReservationFactory(ReservationMapper reservationMapper, ScheduleService scheduleService) {
        this.reservationMapper = reservationMapper;
        this.scheduleService = scheduleService;
    }


    public ReservationEntity create(
            ReservationRequestDto dto,
            CourtEntity court,
            PlayerEntity player,
            StatusReservation status
    ) {
        ReservationEntity reservation = reservationMapper.toModel(dto);

        applyScheduleAndStatus(
                reservation,
                court,
                player,
                reservation.getDay(),
                reservation.getStartTime(),
                status
        );

        return reservation;
    }

    public void applyScheduleAndStatus(
            ReservationEntity reservation,
            CourtEntity court,
            PlayerEntity player,
            LocalDate day,
            LocalTime time,
            StatusReservation status
    ) {
        ScheduleEntity schedule = scheduleService.getAndValidateSchedule(
                court.getId(),
                day,
                time
        );

        reservation.setCourt(court);
        reservation.setPlayer(player);
        reservation.setDay(day);
        reservation.setStartTime(time);
        reservation.setStatus(status);
        reservation.setDuration(schedule.getSlotDuration());
    }
}