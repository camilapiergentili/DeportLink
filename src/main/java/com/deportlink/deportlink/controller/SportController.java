package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.sport.*;
import com.deportlink.deportlink.mapper.dto.SportMapper;
import com.deportlink.deportlink.domain.model.Sport;
import com.deportlink.deportlink.dto.request.SportRequestDto;
import com.deportlink.deportlink.dto.response.SportResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sports")
@RequiredArgsConstructor
public class SportController {

    private final CreateSportUseCase createSportUseCase;
    private final GetSportByIdUseCase getSportByIdUseCase;
    private final GetAllSportsUseCase getAllSportsUseCase;
    private final DeleteSportUseCase deleteSportUseCase;
    private final SportMapper sportMapper;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SportResponseDto> create(@Valid @RequestBody SportRequestDto sportDto) {
        Sport sport = createSportUseCase.execute(sportDto.getNameSport());
        return ResponseEntity.status(HttpStatus.CREATED).body(sportMapper.toResponse(sport));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SportResponseDto>> getAll() {
        return ResponseEntity.ok(getAllSportsUseCase.execute().stream().map(sportMapper::toResponse).toList());
    }

    @GetMapping("/{idSport}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SportResponseDto> getById(@PathVariable long idSport) {
        return ResponseEntity.ok(sportMapper.toResponse(getSportByIdUseCase.execute(idSport)));
    }

    @DeleteMapping("/{idSport}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> delete(@PathVariable long idSport) {
        deleteSportUseCase.execute(idSport);
        return ResponseEntity.ok(Map.of("message", "Deporte eliminado con éxito"));
    }
}