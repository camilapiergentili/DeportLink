package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.SportRequestDto;
import com.deportlink.deportlink.dto.response.SportResponseDto;
import com.deportlink.deportlink.service.implementation.SportServiceImplementation;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sports")
@AllArgsConstructor
public class SportController {

    private final SportServiceImplementation sportService;

    @PostMapping
    public ResponseEntity<SportResponseDto> create(@Valid @RequestBody SportRequestDto sportDto){
        SportResponseDto sportResponse = sportService.create(sportDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(sportResponse);
    }

    @GetMapping
    public ResponseEntity<List<SportResponseDto>> getAll() {
        return ResponseEntity.ok(sportService.getAll());
    }

    @GetMapping("/{idSport}")
    public ResponseEntity<SportResponseDto> getById(@PathVariable long idSport){
        SportResponseDto sportResponse = sportService.getByIdResponse(idSport);
        return ResponseEntity.ok(sportResponse);
    }

    @DeleteMapping("/{idSport}")
    public ResponseEntity<Object> delete(@PathVariable long idSport){
        sportService.delete(idSport);
        return ResponseEntity.ok(Map.of("mesaage", "Deporte eliminado con exito"));
    }


}
