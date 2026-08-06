package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.branch.*;
import com.deportlink.deportlink.mapper.dto.AddressMapper;
import com.deportlink.deportlink.mapper.dto.BranchMapper;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.dto.request.BranchRequestDto;
import com.deportlink.deportlink.dto.response.BranchResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/branches/owner")
@RequiredArgsConstructor
public class BranchOwnerController {

    private final CreateBranchUseCase createBranchUseCase;
    private final UpdateBranchUseCase updateBranchUseCase;
    private final DeleteBranchUseCase deleteBranchUseCase;
    private final ActivateBranchUseCase activateBranchUseCase;
    private final DeactivateBranchUseCase deactivateBranchUseCase;
    private final BranchMapper branchMapper;
    private final AddressMapper addressMapper;

    @PostMapping
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @clubAuthorization.isOwnerOfClub(#branchDto.idClub, authentication))
    """)
    public ResponseEntity<BranchResponseDto> create(@RequestBody @Valid BranchRequestDto branchDto) {
        Branch branch = createBranchUseCase.execute(
                branchDto.getName(),
                addressMapper.toDomain(branchDto.getAddressRequestDto()),
                branchDto.getIdClub()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(branchMapper.toResponse(branch));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @branchAuthorization.isOwnerOfBranch(#id, authentication))
    """)
    public ResponseEntity<Object> delete(@PathVariable long id) {
        deleteBranchUseCase.execute(id);
        return ResponseEntity.ok(Map.of("message", "Sucursal eliminada con éxito"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @branchAuthorization.isOwnerOfBranch(#id, authentication))
    """)
    public ResponseEntity<Object> update(@PathVariable long id,
                                         @Valid @RequestBody BranchRequestDto branchDto) {
        updateBranchUseCase.execute(id, branchDto.getName(), addressMapper.toDomain(branchDto.getAddressRequestDto()));
        return ResponseEntity.ok(Map.of("message", "Sucursal actualizada con éxito"));
    }

    @PatchMapping("/{idBranch}/activate")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @branchAuthorization.isOwnerOfBranch(#idBranch, authentication))
    """)
    public ResponseEntity<Object> activate(@PathVariable long idBranch) {
        activateBranchUseCase.execute(idBranch);
        return ResponseEntity.ok(Map.of("message", "Sucursal activada con éxito"));
    }

    @PatchMapping("/{idBranch}/desactivate")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @branchAuthorization.isOwnerOfBranch(#idBranch, authentication))
    """)
    public ResponseEntity<Object> deactivate(@PathVariable long idBranch) {
        deactivateBranchUseCase.execute(idBranch);
        return ResponseEntity.ok(Map.of("message", "Sucursal desactivada con éxito"));
    }
}