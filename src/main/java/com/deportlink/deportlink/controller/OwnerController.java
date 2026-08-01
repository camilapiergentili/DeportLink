package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.OwnerResponseDto;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;
import com.deportlink.deportlink.model.entity.UserMain;
import com.deportlink.deportlink.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;


import java.net.URI;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;

    @PostMapping
    public ResponseEntity<OwnerResponseDto> register(
            @RequestBody @Valid OwnerRequestDto dto)
            throws OwnerAlreadyExistsException {

        OwnerResponseDto owner = ownerService.register(dto);

        URI location = URI.create("/api/owners/" + owner.getId());

        return ResponseEntity.created(location).body(owner);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OwnerResponseDto>> getAll() {
        return ResponseEntity.ok(ownerService.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OwnerResponseDto> getById(@PathVariable long id) {
        return ResponseEntity.ok(ownerService.getByIdResponse(id));
    }

    @GetMapping("/my-profile")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<OwnerResponseDto> profileOwner(
            @AuthenticationPrincipal UserMain user) {

        return ResponseEntity.ok(ownerService.getByIdResponse(user.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and #id == principal.id)
    """)
    public ResponseEntity<Object> update(
            @PathVariable long id,
            @RequestBody @Valid OwnerRequestDto ownerDto) {

        ownerService.update(id, ownerDto);
        return ResponseEntity.ok(
                Map.of("message", "Dueño actualizado con éxito")
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and #id == principal.id)
    """)
    public ResponseEntity<Object> delete(@PathVariable long id) {
        ownerService.deleteById(id);
        return ResponseEntity.ok(
                Map.of("message", "Dueño eliminado con éxito")
        );
    }
}