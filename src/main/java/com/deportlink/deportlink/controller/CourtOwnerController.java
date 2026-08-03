package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.court.*;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.dto.request.CourtRequestDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/courts/owner")
@RequiredArgsConstructor
public class CourtOwnerController {

    private final CreateCourtUseCase createCourtUseCase;
    private final UpdateCourtUseCase updateCourtUseCase;
    private final UpdateCourtPriceUseCase updateCourtPriceUseCase;
    private final DeleteCourtUseCase deleteCourtUseCase;
    private final ActivateCourtUseCase activateCourtUseCase;
    private final DeactivateCourtUseCase deactivateCourtUseCase;
    private final CourtController courtController;

    @PostMapping
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @branchAuthorization.isOwnerOfBranch(#courtDto.idBranch, authentication))
    """)
    public ResponseEntity<CourtResponseDto> create(@Valid @RequestBody CourtRequestDto courtDto) {
        Court court = createCourtUseCase.execute(
                courtDto.getName(), courtDto.getPricePerHour(),
                courtDto.getIdBranch(), courtDto.getIdSport()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(courtController.toResponse(court));
    }

    @DeleteMapping("/{idCourt}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @courtAuthorization.isOwnerOfCourt(#idCourt, authentication))
    """)
    public ResponseEntity<Object> delete(@PathVariable long idCourt) {
        deleteCourtUseCase.execute(idCourt);
        return ResponseEntity.ok(Map.of("message", "Cancha eliminada con éxito"));
    }

    @PutMapping("/{idCourt}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @courtAuthorization.isOwnerOfCourt(#idCourt, authentication)
            and @branchAuthorization.isOwnerOfBranch(#courtDto.idBranch, authentication))
    """)
    public ResponseEntity<CourtResponseDto> update(
            @PathVariable long idCourt,
            @Valid @RequestBody CourtRequestDto courtDto) {
        Court court = updateCourtUseCase.execute(idCourt, courtDto.getName(), courtDto.getIdSport());
        return ResponseEntity.ok(courtController.toResponse(court));
    }

    @PatchMapping("/{idCourt}/price")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @courtAuthorization.isOwnerOfCourt(#idCourt, authentication))
    """)
    public ResponseEntity<Object> updatePrice(
            @PathVariable long idCourt,
            @RequestParam double newPrice) {
        updateCourtPriceUseCase.execute(idCourt, newPrice);
        return ResponseEntity.ok(Map.of("message", "El precio fue actualizado con éxito"));
    }

    @PatchMapping("/{idCourt}/activate")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @courtAuthorization.isOwnerOfCourt(#idCourt, authentication))
    """)
    public ResponseEntity<Object> activate(@PathVariable long idCourt) {
        activateCourtUseCase.execute(idCourt);
        return ResponseEntity.ok(Map.of("message", "La cancha fue activada con éxito"));
    }

    @PatchMapping("/{idCourt}/deactivate")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @courtAuthorization.isOwnerOfCourt(#idCourt, authentication))
    """)
    public ResponseEntity<Object> deactivate(@PathVariable long idCourt) {
        deactivateCourtUseCase.execute(idCourt);
        return ResponseEntity.ok(Map.of("message", "La cancha fue desactivada con éxito"));
    }
}