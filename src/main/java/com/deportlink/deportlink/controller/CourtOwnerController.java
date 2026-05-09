package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.CourtRequestDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.service.CourtOwnerService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/courts/owners")
@AllArgsConstructor
public class CourtOwnerController {

    private CourtOwnerService courtService;

    @PostMapping
    public ResponseEntity<CourtResponseDto> create(@Valid @RequestBody CourtRequestDto courtDto){
        CourtResponseDto courtResponse = courtService.create(courtDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(courtResponse);
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
