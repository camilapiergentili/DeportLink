package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.owner.*;
import com.deportlink.deportlink.domain.model.Owner;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.OwnerResponseDto;
import com.deportlink.deportlink.model.entity.UserMain;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
public class OwnerController {

    private final RegisterOwnerUseCase registerOwnerUseCase;
    private final GetOwnerByIdUseCase getOwnerByIdUseCase;
    private final GetAllOwnersUseCase getAllOwnersUseCase;
    private final UpdateOwnerUseCase updateOwnerUseCase;
    private final DeleteOwnerUseCase deleteOwnerUseCase;

    @PostMapping
    public ResponseEntity<OwnerResponseDto> register(@RequestBody @Valid OwnerRequestDto dto) {
        Owner owner = registerOwnerUseCase.execute(dto);
        URI location = URI.create("/api/owners/" + owner.id());
        return ResponseEntity.created(location).body(toResponse(owner));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OwnerResponseDto>> getAll() {
        return ResponseEntity.ok(getAllOwnersUseCase.execute().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OwnerResponseDto> getById(@PathVariable long id) {
        return ResponseEntity.ok(toResponse(getOwnerByIdUseCase.execute(id)));
    }

    @GetMapping("/my-profile")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<OwnerResponseDto> profileOwner(@AuthenticationPrincipal UserMain user) {
        return ResponseEntity.ok(toResponse(getOwnerByIdUseCase.execute(user.getId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and #id == principal.id)
    """)
    public ResponseEntity<Object> update(
            @PathVariable long id,
            @RequestBody @Valid OwnerRequestDto dto) {

        updateOwnerUseCase.execute(id, dto);
        return ResponseEntity.ok(Map.of("message", "Dueño actualizado con éxito"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and #id == principal.id)
    """)
    public ResponseEntity<Object> delete(@PathVariable long id) {
        deleteOwnerUseCase.execute(id);
        return ResponseEntity.ok(Map.of("message", "Dueño eliminado con éxito"));
    }

    private OwnerResponseDto toResponse(Owner owner) {
        OwnerResponseDto dto = new OwnerResponseDto();
        dto.setId(owner.id());
        dto.setFirstName(owner.firstName());
        dto.setLastName(owner.lastName());
        dto.setEmail(owner.email());
        dto.setPhone(owner.phone());
        dto.setDni(String.valueOf(owner.dni()));
        dto.setCuil(owner.cuil());
        if (owner.dateOfBirth() != null) {
            dto.setDateOfBirth(owner.dateOfBirth().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        }
        dto.setClubs(null);
        return dto;
    }
}