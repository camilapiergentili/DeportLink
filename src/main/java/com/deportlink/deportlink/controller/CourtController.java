package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.CourtRequestDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.service.CourtService;
import com.deportlink.deportlink.service.implementation.CourtServiceImplementation;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courts")
@AllArgsConstructor
public class CourtController {

    private final CourtService courtService;

    @GetMapping("/{idCourt}")
    public ResponseEntity<CourtResponseDto> getById(@PathVariable long idCourt){
        CourtResponseDto courtResponse = courtService.getByIdResponse(idCourt);
        return ResponseEntity.ok(courtResponse);
    }

    @GetMapping("/{idCourt}/active")
    public ResponseEntity<CourtResponseDto> getByIdApprovedAndActive(@PathVariable long idCourt) {
        return ResponseEntity.ok(courtService.getByIdApprovedAndActive(idCourt));
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

}
