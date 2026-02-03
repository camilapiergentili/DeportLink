package com.deportlink.DeportLink.controller;


import com.deportlink.DeportLink.dto.request.PlayerRequestDto;
import com.deportlink.DeportLink.dto.response.PlayerResponseDto;
import com.deportlink.DeportLink.service.implementation.PlayerServiceImplementation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerServiceImplementation playerService;

    @GetMapping("{id}")
    public ResponseEntity<PlayerResponseDto> getById(@Valid @PathVariable long id){
        PlayerResponseDto playerResponse = playerService.getByIdResponse(id);
        return ResponseEntity.ok(playerResponse);
    }

    @PostMapping("/")
    public ResponseEntity<PlayerResponseDto> register(@Valid @RequestBody PlayerRequestDto playerDto){
        PlayerResponseDto playerResponse = playerService.register(playerDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(playerResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlayerResponseDto> update(
            @PathVariable long id,
            @Valid @RequestBody PlayerRequestDto playerDto){

        PlayerResponseDto playerResponse = playerService.update(id, playerDto);

        return ResponseEntity.ok(playerResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@Valid @PathVariable long id){
        playerService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Jugador eliminado con éxito"));

    }

}
