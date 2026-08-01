package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.AppointmentRequestDto;
import com.deportlink.deportlink.dto.response.ScheduleResponseDto;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.service.AppointmentService;
import com.deportlink.deportlink.service.CourtService;
import com.deportlink.deportlink.service.ReservationService;
import com.deportlink.deportlink.service.ScheduleService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class AppointmentServiceImplementation implements AppointmentService {

    private final CourtService courtService;
    private final ScheduleService scheduleService;
    private final ReservationService reservationService;

    @Override
    @Transactional
    public List<LocalTime> available(AppointmentRequestDto appointmentDto) {
        log.info("Fetching available appointments: courtId={}, day={}", appointmentDto.getIdCourt(), appointmentDto.getDay());

        List<LocalTime> allSlots = generateSlots(appointmentDto);
        Set<LocalTime> bookedSlots = reservationService.getByCourtAndDay(
                appointmentDto.getIdCourt(), appointmentDto.getDay());

        // Set.contains() es O(1) — removeIf recorre allSlots una sola vez
        allSlots.removeIf(bookedSlots::contains);
        log.info("Available appointments retrieved: courtId={}, availableSlots={}", appointmentDto.getIdCourt(), allSlots.size());

        return allSlots;
    }

    private List<LocalTime> generateSlots(AppointmentRequestDto appointmentDto) {
        CourtEntity courtEntity = courtService.getById(appointmentDto.getIdCourt());
        ScheduleResponseDto schedule = scheduleService.getByDay(courtEntity.getId(), appointmentDto.getDay());
        return scheduleService.generateSlots(
                schedule.getOpeningTime(),
                schedule.getClosingTime(),
                schedule.getSlotDuration()
        );
    }

}
