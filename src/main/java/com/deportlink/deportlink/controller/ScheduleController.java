package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.ScheduleRequestDto;
import com.deportlink.deportlink.dto.response.ScheduleResponseDto;
import com.deportlink.deportlink.service.implementation.ScheduleServiceImplementacion;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
@AllArgsConstructor
public class ScheduleController {

    final private ScheduleServiceImplementacion scheduleService;

    @PostMapping("/court/{idCourt}")
    public ResponseEntity<Object> add(@PathVariable long idCourt,
                                      @Valid @RequestBody List<ScheduleRequestDto> listSchedules){
        scheduleService.addSchedule(idCourt, listSchedules);
        return ResponseEntity.status(HttpStatus.CREATED).body("Agenda creada con exito");
    }

    @DeleteMapping("/{idSchedule}/court/{idCourt}/")
    public ResponseEntity<Object> delete(@RequestParam long idSchedule, @RequestParam long idCourt){
        scheduleService.deleteSchedule(idSchedule, idCourt);
        return ResponseEntity.ok(Map.of("message", "Agenda eliminada con exito"));
    }

    @PutMapping("/{idSchedule}/court/{idCourt}/")
    public ResponseEntity<Object> update(@PathVariable long idSchedule,
                                         @PathVariable long idCourt,
                                         @RequestParam String openingNew,
                                         @RequestParam String closingNew){
        scheduleService.updateSchedule(idSchedule, idCourt, openingNew, closingNew);
        return ResponseEntity.ok(Map.of("message", "Agenda modificada con exito"));
    }

    @GetMapping("/court/{idCourt}")
    public ResponseEntity<List<ScheduleResponseDto>> getByCourt(@PathVariable long idCourt){
        return ResponseEntity.ok(scheduleService.getAllByCourt(idCourt));
    }

    @GetMapping("/court/{idCourt}/day")
    public ResponseEntity<ScheduleResponseDto> getByDay(@PathVariable long idCourt,
                                                        @RequestParam LocalDate day) {
        return ResponseEntity.ok(scheduleService.getByDay(idCourt, day));
    }

}
