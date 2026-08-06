package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.court.*;
import com.deportlink.deportlink.mapper.dto.CourtMapper;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courts")
@RequiredArgsConstructor
public class CourtController {

    private final GetCourtByIdUseCase getCourtByIdUseCase;
    private final GetActiveCourtUseCase getActiveCourtUseCase;
    private final GetApprovedCourtsUseCase getApprovedCourtsUseCase;
    private final GetApprovedCourtsByBranchUseCase getApprovedCourtsByBranchUseCase;
    private final GetCourtsByBranchAndSportUseCase getCourtsByBranchAndSportUseCase;
    private final CourtMapper courtMapper;

    @GetMapping("/{idCourt}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @courtAuthorization.isOwnerOfCourt(#idCourt, authentication))
    """)
    public ResponseEntity<CourtResponseDto> getById(@PathVariable long idCourt) {
        return ResponseEntity.ok(courtMapper.toResponse(getCourtByIdUseCase.execute(idCourt)));
    }

    @GetMapping("/{idCourt}/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CourtResponseDto> getByIdApprovedAndActive(@PathVariable long idCourt) {
        return ResponseEntity.ok(courtMapper.toResponse(getActiveCourtUseCase.execute(idCourt)));
    }

    @GetMapping("/branch/{idBranch}/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CourtResponseDto>> getAllByBranchActiveAndApproved(@PathVariable long idBranch) {
        List<CourtResponseDto> result = getApprovedCourtsByBranchUseCase
                .execute(idBranch, Pageable.unpaged()).getContent()
                .stream().map(courtMapper::toResponse).toList();
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/branch/{idBranch}/active/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<CourtResponseDto>> getAllByBranchActiveAndApprovedPaginated(
            @PathVariable long idBranch,
            @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        Page<CourtResponseDto> result = getApprovedCourtsByBranchUseCase
                .execute(idBranch, pageable).map(courtMapper::toResponse);
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CourtResponseDto>> getAllActiveAndApproved() {
        List<CourtResponseDto> result = getApprovedCourtsUseCase
                .execute(Pageable.unpaged()).getContent()
                .stream().map(courtMapper::toResponse).toList();
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/active/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<CourtResponseDto>> getAllActiveAndApprovedPaginated(
            @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        Page<CourtResponseDto> result = getApprovedCourtsUseCase.execute(pageable).map(courtMapper::toResponse);
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/branch/{idBranch}/sport/{idSport}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CourtResponseDto>> getCourtsByBranchAndSport(
            @PathVariable long idBranch,
            @PathVariable long idSport) {
        List<CourtResponseDto> result = getCourtsByBranchAndSportUseCase
                .execute(idBranch, idSport).stream().map(courtMapper::toResponse).toList();
        return ResponseEntity.ok(result);
    }
}