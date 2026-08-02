package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.player.*;
import com.deportlink.deportlink.domain.model.Player;
import com.deportlink.deportlink.domain.model.PlayerAddress;
import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.response.AddressResponseDto;
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
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final RegisterPlayerUseCase registerPlayerUseCase;
    private final GetPlayerByIdUseCase getPlayerByIdUseCase;
    private final UpdatePlayerUseCase updatePlayerUseCase;
    private final DeletePlayerUseCase deletePlayerUseCase;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlayerResponseDto> getById(@PathVariable long id) {
        return ResponseEntity.ok(toResponse(getPlayerByIdUseCase.execute(id)));
    }

    @GetMapping("/my-profile")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<PlayerResponseDto> profilePlayer(@AuthenticationPrincipal UserMain user) {
        return ResponseEntity.ok(toResponse(getPlayerByIdUseCase.execute(user.getId())));
    }

    @PostMapping
    public ResponseEntity<PlayerResponseDto> register(@RequestBody @Valid PlayerRequestDto playerDto) {
        Player player = registerPlayerUseCase.execute(playerDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(player));
    }

    @PutMapping("/{id}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('PLAYER') and #id == principal.id)
    """)
    public ResponseEntity<PlayerResponseDto> update(
            @PathVariable long id,
            @RequestBody @Valid PlayerRequestDto playerDto) {

        return ResponseEntity.ok(toResponse(updatePlayerUseCase.execute(id, playerDto)));
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

    private PlayerResponseDto toResponse(Player player) {
        PlayerResponseDto dto = new PlayerResponseDto();
        dto.setId(player.id());
        dto.setFirstName(player.firstName());
        dto.setLastName(player.lastName());
        dto.setEmail(player.email());
        dto.setPhone(player.phone());
        Set<AddressResponseDto> addresses = player.addresses().stream()
                .map(this::toAddressResponse).collect(Collectors.toSet());
        dto.setAddresses(addresses);
        return dto;
    }

    private AddressResponseDto toAddressResponse(PlayerAddress addr) {
        AddressResponseDto dto = new AddressResponseDto();
        dto.setStreetName(addr.streetName());
        dto.setNumber(addr.number());
        dto.setCity(addr.city());
        dto.setProvince(addr.province());
        dto.setPostalCode(addr.postalCode());
        dto.setLatitude(addr.latitude());
        dto.setLongitude(addr.longitude());
        return dto;
    }
}