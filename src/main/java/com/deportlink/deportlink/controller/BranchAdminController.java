package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.branch.*;
import com.deportlink.deportlink.mapper.dto.BranchMapper;
import com.deportlink.deportlink.dto.response.BranchResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/branches/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BranchAdminController {

    private final GetBranchByIdUseCase getBranchByIdUseCase;
    private final GetAllBranchesUseCase getAllBranchesUseCase;
    private final ApproveBranchUseCase approveBranchUseCase;
    private final RejectBranchUseCase rejectBranchUseCase;
    private final BranchMapper branchMapper;

    @GetMapping("/{id}")
    public ResponseEntity<BranchResponseDto> getById(@PathVariable long id) {
        return ResponseEntity.ok(branchMapper.toResponse(getBranchByIdUseCase.execute(id)));
    }

    @GetMapping("/{idClub}/club")
    public ResponseEntity<List<BranchResponseDto>> getAll(@PathVariable long idClub) {
        List<BranchResponseDto> result = getAllBranchesUseCase.execute(idClub)
                .stream().map(branchMapper::toResponse).toList();
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<Object> approve(@PathVariable long id) {
        approveBranchUseCase.execute(id);
        return ResponseEntity.ok(Map.of("message", "Sucursal aprobada con éxito"));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<Object> reject(@PathVariable long id) {
        rejectBranchUseCase.execute(id);
        return ResponseEntity.ok(Map.of("message", "Sucursal rechazada con éxito"));
    }
}