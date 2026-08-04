package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.schedule.*;
import com.deportlink.deportlink.controller.mapper.ScheduleMapper;
import com.deportlink.deportlink.dto.request.ScheduleRequestDto;
import com.deportlink.deportlink.dto.response.ScheduleResponseDto;
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

    private final AddScheduleUseCase addScheduleUseCase;
    private final DeleteScheduleUseCase deleteScheduleUseCase;
    private final UpdateScheduleUseCase updateScheduleUseCase;
    private final GetAllSchedulesByCourtUseCase getAllSchedulesByCourtUseCase;
    private final GetScheduleByDayUseCase getScheduleByDayUseCase;
    private final ScheduleMapper scheduleMapper;

    @PostMapping("/court/{idCourt}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or @courtAuthorization.isOwnerOfCourt(#idCourt, authentication)
    """)
    public ResponseEntity<Object> add(
            @PathVariable long idCourt,
            @Valid @RequestBody List<ScheduleRequestDto> schedules) {

        addScheduleUseCase.execute(idCourt, schedules);
        return ResponseEntity.status(HttpStatus.CREATED)
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

        deleteScheduleUseCase.execute(idSchedule, idCourt);
        return ResponseEntity.ok(Map.of("message", "Agenda eliminada con éxito"));
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

        updateScheduleUseCase.execute(idSchedule, idCourt, openingNew, closingNew);
        return ResponseEntity.ok(Map.of("message", "Agenda modificada con éxito"));
    }

    @GetMapping("/court/{idCourt}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ScheduleResponseDto>> getByCourt(@PathVariable long idCourt) {
        return ResponseEntity.ok(
                getAllSchedulesByCourtUseCase.execute(idCourt).stream().map(scheduleMapper::toResponse).toList()
        );
    }

    @GetMapping("/court/{idCourt}/day")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduleResponseDto> getByDay(
            @PathVariable long idCourt,
            @RequestParam LocalDate day) {

        return ResponseEntity.ok(scheduleMapper.toResponse(getScheduleByDayUseCase.execute(idCourt, day)));
    }
}