package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.SportRequestDto;
import com.deportlink.deportlink.dto.response.SportResponseDto;
import com.deportlink.deportlink.service.SportService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/sports")
@AllArgsConstructor
public class SportController {

    private final SportService sportService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SportResponseDto> create(
            @Valid @RequestBody SportRequestDto sportDto) {

        SportResponseDto sport = sportService.create(sportDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(sport);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SportResponseDto>> getAll() {
        return ResponseEntity.ok(sportService.getAll());
    }

    @GetMapping("/{idSport}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SportResponseDto> getById(
            @PathVariable long idSport) {

        return ResponseEntity.ok(sportService.getByIdResponse(idSport));
    }

    @DeleteMapping("/{idSport}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> delete(@PathVariable long idSport) {
        sportService.delete(idSport);
        return ResponseEntity.ok(
                Map.of("message", "Deporte eliminado con éxito")
        );
    }
}