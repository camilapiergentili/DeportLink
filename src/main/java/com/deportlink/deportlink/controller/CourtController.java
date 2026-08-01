package com.deportlink.deportlink.controller;


import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.service.CourtService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courts")
@AllArgsConstructor
public class CourtController {

    private final CourtService courtService;

    @GetMapping("/{idCourt}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @courtAuthorization.isOwnerOfCourt(
                #idCourt, authentication))
    """)
    public ResponseEntity<CourtResponseDto> getById(
            @PathVariable long idCourt) {

        return ResponseEntity.ok(courtService.getByIdResponse(idCourt));
    }

    @GetMapping("/{idCourt}/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CourtResponseDto> getByIdApprovedAndActive(
            @PathVariable long idCourt) {

        return ResponseEntity.ok(
                courtService.getByIdApprovedAndActive(idCourt)
        );
    }

    @GetMapping("/branch/{idBranch}/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CourtResponseDto>>
    getAllByBranchActiveAndApproved(@PathVariable long idBranch) {

        return ResponseEntity.ok(
                courtService.getAllByBranchActiveAndApproved(idBranch)
        );
    }

    @GetMapping("/branch/{idBranch}/active/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<CourtResponseDto>>
    getAllByBranchActiveAndApprovedPaginated(
            @PathVariable long idBranch,
            @PageableDefault(size = 12, sort = "id") Pageable pageable) {

        return ResponseEntity.ok(
                courtService.getAllByBranchActiveAndApprovedPaginated(
                        idBranch, pageable)
        );
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CourtResponseDto>> getAllActiveAndApproved() {
        return ResponseEntity.ok(courtService.getAllActiveAndApproved());
    }

    @GetMapping("/active/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<CourtResponseDto>>
    getAllActiveAndApprovedPaginated(
            @PageableDefault(size = 12, sort = "id") Pageable pageable) {

        return ResponseEntity.ok(
                courtService.getAllActiveAndApprovedPaginated(pageable)
        );
    }

    @GetMapping("/branch/{idBranch}/sport/{idSport}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CourtResponseDto>> getCourtsByBranchAndSport(
            @PathVariable long idBranch,
            @PathVariable long idSport) {

        return ResponseEntity.ok(
                courtService.getCourtsByBranchAndSport(idBranch, idSport)
        );
    }
}