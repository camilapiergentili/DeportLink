package com.deportlink.DeportLink.controller;

import com.deportlink.DeportLink.dto.request.OwnerRequestDto;
import com.deportlink.DeportLink.dto.response.OwnerResponseDto;
import com.deportlink.DeportLink.exception.OwnerAlreadyExistsException;
import com.deportlink.DeportLink.service.OwnerService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;


import java.net.URI;

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
}
