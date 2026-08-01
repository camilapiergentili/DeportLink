package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.CourtRequestDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.service.CourtOwnerService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courts/owners")
@AllArgsConstructor
public class CourtOwnerController {

    private final CourtOwnerService courtService;

    @PostMapping
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @courtAuthorization.isOwnerOfBranch(
                #courtDto.idBranch, authentication))
    """)
    public ResponseEntity<CourtResponseDto> create(
            @Valid @RequestBody CourtRequestDto courtDto) {

        CourtResponseDto court = courtService.create(courtDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(court);
    }

    @DeleteMapping("/{idCourt}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @courtAuthorization.isOwnerOfCourt(
                #idCourt, authentication))
    """)
    public ResponseEntity<Object> delete(@PathVariable long idCourt) {
        courtService.delete(idCourt);
        return ResponseEntity.ok(
                Map.of("message", "Cancha eliminada con éxito")
        );
    }

    @PutMapping("/{idCourt}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @courtAuthorization.isOwnerOfCourt(
                #idCourt, authentication)
            and @courtAuthorization.isOwnerOfBranch(
                #courtDto.idBranch, authentication))
    """)
    public ResponseEntity<CourtResponseDto> update(
            @PathVariable long idCourt,
            @Valid @RequestBody CourtRequestDto courtDto) {

        return ResponseEntity.ok(courtService.update(idCourt, courtDto));
    }

    @PatchMapping("/{idCourt}/price")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @courtAuthorization.isOwnerOfCourt(
                #idCourt, authentication))
    """)
    public ResponseEntity<Object> updatePrice(
            @PathVariable long idCourt,
            @RequestParam double newPrice) {

        courtService.updatePrice(idCourt, newPrice);
        return ResponseEntity.ok(
                Map.of("message", "El precio fue actualizado con éxito")
        );
    }

    @PatchMapping("/{idCourt}/activate")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @courtAuthorization.isOwnerOfCourt(
                #idCourt, authentication))
    """)
    public ResponseEntity<Object> activate(
            @RequestParam long idBranch,
            @PathVariable long idCourt) {

        courtService.activateCourt(idBranch, idCourt);
        return ResponseEntity.ok(
                Map.of("message", "La cancha fue activada con éxito")
        );
    }

    @PatchMapping("/{idCourt}/deactivate")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @courtAuthorization.isOwnerOfCourt(
                #idCourt, authentication))
    """)
    public ResponseEntity<Object> deactivate(
            @RequestParam long idBranch,
            @PathVariable long idCourt) {

        courtService.deactivateCourt(idBranch, idCourt);
        return ResponseEntity.ok(
                Map.of("message", "La cancha fue desactivada con éxito")
        );
    }
}