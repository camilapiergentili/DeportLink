package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.CourtRequestDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.service.implementation.court.CourtServiceImplementation;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.apache.coyote.Response;
import org.apache.logging.log4j.spi.ObjectThreadContextMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courts")
@AllArgsConstructor
public class CourtController {

    private final CourtServiceImplementation courtService;

    @PostMapping
    public ResponseEntity<CourtResponseDto> create(@Valid @RequestBody CourtRequestDto courtDto){
        CourtResponseDto courtResponse = courtService.create(courtDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(courtResponse);
    }

    @GetMapping("/{idCourt}")
    public ResponseEntity<CourtResponseDto> getById(@PathVariable long idCourt){
        CourtResponseDto courtResponse = courtService.getByIdResponse(idCourt);
        return ResponseEntity.ok(courtResponse);
    }

    @GetMapping("/{idCourt}/active")
    public ResponseEntity<CourtResponseDto> getByIdApprovedAndActive(@PathVariable long idCourt) {
        return ResponseEntity.ok(courtService.getByIdApprovedAndActive(idCourt));
    }

    @GetMapping("/branch/{idBranch}")
    public ResponseEntity<List<CourtResponseDto>> getAllByBranch(@PathVariable long idBranch) {
        return ResponseEntity.ok(courtService.getAllBranch(idBranch));
    }

    @GetMapping
    public ResponseEntity<List<CourtResponseDto>> getAll(){
        return ResponseEntity.ok(courtService.getAll());
    }

    @GetMapping("/branch/{idBranch}/active")
    public ResponseEntity<List<CourtResponseDto>> getAllByBranchActiveAndApproved(@PathVariable long idBranch) {
        return ResponseEntity.ok(courtService.getAllByBranchActiveAndApproved(idBranch));
    }

    @GetMapping("/active")
    public ResponseEntity<List<CourtResponseDto>> getAllActiveAndApproved() {
        return ResponseEntity.ok(courtService.getAllActiveAndApproved());
    }

    @GetMapping("/branch/{idBranch}/sport/{idSport}")
    public ResponseEntity<List<CourtResponseDto>> getCourtsByBranchAndSport(
            @PathVariable long idBranch,
            @PathVariable long idSport) {
        return ResponseEntity.ok(courtService.getCourtsByBranchAndSport(idBranch, idSport));
    }

    @DeleteMapping("/{idCourt}")
    public ResponseEntity<Object> detele(@PathVariable long idCourt){
        courtService.delete(idCourt);
        return ResponseEntity.ok(Map.of("message", "Cancha eliminada con exito"));
    }

    @PutMapping("/{idCourt}")
    public ResponseEntity<CourtResponseDto> update(@PathVariable long idCourt,
                                                   @Valid @RequestBody CourtRequestDto courtDto){
        CourtResponseDto courtResponse = courtService.update(idCourt, courtDto);
        return ResponseEntity.ok(courtResponse);
    }

    @PatchMapping("/{idCourt}/price")
    public ResponseEntity<Object> updatePrice(@PathVariable long idCourt,
                                              @RequestParam double newPrice){
        courtService.updatePrice(idCourt, newPrice);
        return ResponseEntity.ok(Map.of("message", "El precio ha sido actualizado con exito"));
    }

    @PatchMapping("/{idCourt}/activate")
    public ResponseEntity<Object> activate(@RequestParam long idBranch,
                                           @PathVariable long idCourt){
        courtService.activateCourt(idBranch, idCourt);
        return ResponseEntity.ok(Map.of("message", "La cancha fue activado con exito"));
    }

    @PatchMapping("/{idCourt}/desactivate")
    public ResponseEntity<Object> desactivate(@RequestParam long idBranch,
                                              @PathVariable long idCourt){
        courtService.desactivedCourt(idBranch, idCourt);
        return ResponseEntity.ok(Map.of("message", "La cancha fue activado con exito"));
    }

}
