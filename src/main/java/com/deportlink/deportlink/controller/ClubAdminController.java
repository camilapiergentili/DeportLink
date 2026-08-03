package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.club.ApproveClubUseCase;
import com.deportlink.deportlink.application.usecase.club.RejectClubUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clubs/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ClubAdminController {

    private final ApproveClubUseCase approveClubUseCase;
    private final RejectClubUseCase rejectClubUseCase;

    @PatchMapping("/{idClub}/approve")
    public ResponseEntity<Object> approve(@PathVariable long idClub) {
        approveClubUseCase.execute(idClub);
        return ResponseEntity.ok(Map.of("message", "El club fue aprobado con éxito"));
    }

    @PatchMapping("/{idClub}/reject")
    public ResponseEntity<Object> reject(@PathVariable long idClub) {
        rejectClubUseCase.execute(idClub);
        return ResponseEntity.ok(Map.of("message", "El club fue rechazado con éxito"));
    }
}