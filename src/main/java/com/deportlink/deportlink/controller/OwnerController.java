package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.OwnerResponseDto;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;
import com.deportlink.deportlink.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;


import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/owners")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;

    @PostMapping
    public ResponseEntity<OwnerResponseDto> register(@Valid @RequestBody OwnerRequestDto dto)
            throws OwnerAlreadyExistsException {

        OwnerResponseDto response = ownerService.register(dto);

        URI location = URI.create("/owners" + response.getId());

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OwnerResponseDto> getById(@Valid @PathVariable long id){
        OwnerResponseDto ownerResponse = ownerService.getByIdResponse(id);
        return ResponseEntity.ok(ownerResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@Valid @PathVariable long id,
                                         @RequestBody OwnerRequestDto ownerDto){
        ownerService.update(id, ownerDto);
        return ResponseEntity.ok(Map.of("message", "Owner actualizado con exito"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@Valid @PathVariable long id){
        ownerService.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Owner eliminado con exito"));
    }



}
