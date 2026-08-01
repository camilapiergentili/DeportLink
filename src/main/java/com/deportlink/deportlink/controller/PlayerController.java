package com.deportlink.deportlink.controller;


import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import com.deportlink.deportlink.model.entity.UserMain;
import com.deportlink.deportlink.service.implementation.PlayerServiceImplementation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerServiceImplementation playerService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlayerResponseDto> getById(@PathVariable long id) {
        return ResponseEntity.ok(playerService.getByIdResponse(id));
    }

    @GetMapping("/my-profile")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<PlayerResponseDto> profilePlayer(
            @AuthenticationPrincipal UserMain user) {

        return ResponseEntity.ok(playerService.getByIdResponse(user.getId()));
    }

    @PostMapping
    public ResponseEntity<PlayerResponseDto> register(
            @RequestBody @Valid PlayerRequestDto playerDto) {

        PlayerResponseDto player = playerService.register(playerDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(player);
    }

    @PutMapping("/{id}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('PLAYER') and #id == principal.id)
    """)
    public ResponseEntity<PlayerResponseDto> update(
            @PathVariable long id,
            @RequestBody @Valid PlayerRequestDto playerDto) {

        return ResponseEntity.ok(playerService.update(id, playerDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('PLAYER') and #id == principal.id)
    """)
    public ResponseEntity<Object> delete(@PathVariable long id) {
        playerService.delete(id);
        return ResponseEntity.ok(
                Map.of("message", "Jugador eliminado con éxito")
        );
    }
}