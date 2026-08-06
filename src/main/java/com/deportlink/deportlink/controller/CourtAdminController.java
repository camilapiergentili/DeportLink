package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.court.GetAllCourtsByBranchUseCase;
import com.deportlink.deportlink.application.usecase.court.GetAllCourtsUseCase;
import com.deportlink.deportlink.mapper.dto.CourtMapper;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courts/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CourtAdminController {

    private final GetAllCourtsByBranchUseCase getAllCourtsByBranchUseCase;
    private final GetAllCourtsUseCase getAllCourtsUseCase;
    private final CourtMapper courtMapper;

    @GetMapping("/branch/{idBranch}")
    public ResponseEntity<List<CourtResponseDto>> getAllByBranch(@PathVariable long idBranch) {
        List<CourtResponseDto> result = getAllCourtsByBranchUseCase.execute(idBranch)
                .stream().map(courtMapper::toResponse).toList();
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<List<CourtResponseDto>> getAll() {
        List<CourtResponseDto> result = getAllCourtsUseCase.execute()
                .stream().map(courtMapper::toResponse).toList();
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }
}