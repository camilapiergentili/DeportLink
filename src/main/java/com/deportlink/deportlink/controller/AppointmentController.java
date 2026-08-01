package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.AppointmentRequestDto;
import com.deportlink.deportlink.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/appointment")
@AllArgsConstructor
public class AppointmentController {

    final private AppointmentService appointmentService;

    @PostMapping("/available")
    public ResponseEntity<List<LocalTime>> getAvailable(@RequestBody @Valid AppointmentRequestDto appointmentDto){
        List<LocalTime> available = appointmentService.available(appointmentDto);
        return ResponseEntity.ok(available);
    }
}
