package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.service.ClubAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/clubs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ClubAdminController {

    private final ClubAdminService clubAdminService;

    @PatchMapping("/{idClub}/approve")
    public ResponseEntity<Object> approve(@PathVariable long idClub) {
        clubAdminService.approve(idClub);
        return ResponseEntity.ok(
                Map.of("message", "El club fue aprobado con éxito")
        );
    }

    @PatchMapping("/{idClub}/reject")
    public ResponseEntity<Object> reject(@PathVariable long idClub) {
        clubAdminService.reject(idClub);
        return ResponseEntity.ok(
                Map.of("message", "El club fue rechazado con éxito")
        );
    }
}
