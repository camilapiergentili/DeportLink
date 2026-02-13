package com.deportlink.deportlink.controller;


import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import com.deportlink.deportlink.service.implementation.PlayerServiceImplementation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerServiceImplementation playerService;

    @GetMapping("/{id}")
    public ResponseEntity<PlayerResponseDto> getById(@PathVariable long id){
        PlayerResponseDto playerResponse = playerService.getByIdResponse(id);
        return ResponseEntity.ok(playerResponse);
    }

    @PostMapping
    public ResponseEntity<PlayerResponseDto> register(@RequestBody @Valid PlayerRequestDto playerDto){
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
    public ResponseEntity<Object> delete(@PathVariable long id){
        playerService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Jugador eliminado con éxito"));

    }

}
