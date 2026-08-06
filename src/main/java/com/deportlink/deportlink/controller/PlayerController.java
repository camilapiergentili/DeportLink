package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.player.*;
import com.deportlink.deportlink.mapper.dto.PlayerMapper;
import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import com.deportlink.deportlink.model.entity.UserMain;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final RegisterPlayerUseCase registerPlayerUseCase;
    private final GetPlayerByIdUseCase getPlayerByIdUseCase;
    private final UpdatePlayerUseCase updatePlayerUseCase;
    private final DeletePlayerUseCase deletePlayerUseCase;
    private final PlayerMapper playerMapper;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlayerResponseDto> getById(@PathVariable long id) {
        return ResponseEntity.ok(playerMapper.toResponse(getPlayerByIdUseCase.execute(id)));
    }

    @GetMapping("/my-profile")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<PlayerResponseDto> profilePlayer(@AuthenticationPrincipal UserMain user) {
        return ResponseEntity.ok(playerMapper.toResponse(getPlayerByIdUseCase.execute(user.getId())));
    }

    @PostMapping
    public ResponseEntity<PlayerResponseDto> register(@RequestBody @Valid PlayerRequestDto playerDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(playerMapper.toResponse(registerPlayerUseCase.execute(playerDto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('PLAYER') and #id == principal.id)
    """)
    public ResponseEntity<PlayerResponseDto> update(
            @PathVariable long id,
            @RequestBody @Valid PlayerRequestDto playerDto) {

        return ResponseEntity.ok(playerMapper.toResponse(updatePlayerUseCase.execute(id, playerDto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('PLAYER') and #id == principal.id)
    """)
    public ResponseEntity<Object> delete(@PathVariable long id) {
        deletePlayerUseCase.execute(id);
        return ResponseEntity.ok(Map.of("message", "Jugador eliminado con éxito"));
    }
}