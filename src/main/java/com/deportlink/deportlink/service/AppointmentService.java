package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.AppointmentRequestDto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalTime;
import java.util.List;

public interface AppointmentService  {
    List<LocalTime> available(AppointmentRequestDto appointmentDto);
}
