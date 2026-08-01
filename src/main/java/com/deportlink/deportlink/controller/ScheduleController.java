package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.ScheduleRequestDto;
import com.deportlink.deportlink.dto.response.ScheduleResponseDto;
import com.deportlink.deportlink.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping("/court/{idCourt}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or @courtAuthorization.isOwnerOfCourt(#idCourt, authentication)
    """)
    public ResponseEntity<Object> add(
            @PathVariable long idCourt,
            @Valid @RequestBody List<ScheduleRequestDto> schedules) {

        scheduleService.addSchedule(idCourt, schedules);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("message", "Agenda creada con éxito"));
    }

    @DeleteMapping("/{idSchedule}/court/{idCourt}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or @courtAuthorization.isOwnerOfCourt(#idCourt, authentication)
    """)
    public ResponseEntity<Object> delete(
            @PathVariable long idSchedule,
            @PathVariable long idCourt) {

        scheduleService.deleteSchedule(idSchedule, idCourt);
        return ResponseEntity.ok(
                Map.of("message", "Agenda eliminada con éxito")
        );
    }

    @PutMapping("/{idSchedule}/court/{idCourt}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or @courtAuthorization.isOwnerOfCourt(#idCourt, authentication)
    """)
    public ResponseEntity<Object> update(
            @PathVariable long idSchedule,
            @PathVariable long idCourt,
            @RequestParam String openingNew,
            @RequestParam String closingNew) {

        scheduleService.updateSchedule(idSchedule, idCourt, openingNew, closingNew);
        return ResponseEntity.ok(
                Map.of("message", "Agenda modificada con éxito")
        );
    }

    @GetMapping("/court/{idCourt}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ScheduleResponseDto>> getByCourt(
            @PathVariable long idCourt) {

        return ResponseEntity.ok(scheduleService.getAllByCourt(idCourt));
    }

    @GetMapping("/court/{idCourt}/day")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduleResponseDto> getByDay(
            @PathVariable long idCourt,
            @RequestParam LocalDate day) {

        return ResponseEntity.ok(scheduleService.getByDay(idCourt, day));
    }
}