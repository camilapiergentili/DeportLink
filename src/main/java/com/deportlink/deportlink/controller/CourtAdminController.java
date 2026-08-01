package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.service.CourtAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CourtAdminController {

    private final CourtAdminService courtAdminService;

    @GetMapping("/branch/{idBranch}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CourtResponseDto>> getAllByBranch(
            @PathVariable long idBranch) {

        return ResponseEntity.ok(
                courtAdminService.getAllBranch(idBranch)
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CourtResponseDto>> getAll() {
        return ResponseEntity.ok(courtAdminService.getAll());
    }
}
