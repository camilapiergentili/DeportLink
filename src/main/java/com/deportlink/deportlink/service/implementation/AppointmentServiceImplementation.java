package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.AppointmentRequestDto;
import com.deportlink.deportlink.dto.response.ScheduleResponseDto;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.service.implementation.court.CourtServiceImplementation;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class AppointmentServiceImplementation {

    private final CourtServiceImplementation courtService;
    private final ScheduleServiceImplementacion scheduleService;
    private final ReservationServiceImplementation reservationService;

    public List<LocalTime> available(AppointmentRequestDto appointmentDto){
        List<LocalTime> allTimes = generate(appointmentDto);
        List<LocalTime> busyTimes = reservationService.getForDay(appointmentDto.getIdCourt(), appointmentDto.getDay());

        allTimes.removeIf(busyTimes::contains);

        return allTimes;
    }

    private List<LocalTime> generate(AppointmentRequestDto appointmentDto){

        CourtEntity courtEntity = courtService.getById(appointmentDto.getIdCourt());
        LocalDate dayOfAppointment = LocalDate.parse(appointmentDto.getDay());
        ScheduleResponseDto schedulesByDay = scheduleService.getByDay(courtEntity.getId(), dayOfAppointment);

        List<LocalTime> appointment = new ArrayList<>();

        LocalTime open = LocalTime.parse(schedulesByDay.getOpeningTime());
        LocalTime close = LocalTime.parse(schedulesByDay.getClosingTime());

        long duration = schedulesByDay.getSlotDuration();

        LocalTime current = open;

        while(!current.plusMinutes(duration).isAfter(close)){
            appointment.add(current);
            current = current.plusMinutes(duration);
        }

        return appointment;
    }

}
