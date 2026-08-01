package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.ClubRequestDto;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;
import com.deportlink.deportlink.service.ClubOwnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubOwnerController {

    private final ClubOwnerService clubOwnerService;

    @PostMapping
    @PreAuthorize("""
    hasRole('ADMIN')
    or (hasRole('OWNER') and #clubDto.ownerIds.contains(principal.id))
    """)
    public ResponseEntity<ClubResponseDto> create(@RequestBody @Valid ClubRequestDto clubDto){
        ClubResponseDto clubResponse = clubOwnerService.create(clubDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(clubResponse);
    }

    @DeleteMapping("/{idClub}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @clubAuthorization.isOwnerOfClub(
                #idClub, authentication))
    """)
    public ResponseEntity<Object> delete(@PathVariable long idClub){
        clubOwnerService.delete(idClub);
        return ResponseEntity.ok("Club eliminado con exito");
    }

    @PutMapping("/{idClub}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @clubAuthorization.isOwnerOfClub(
                #idClub, authentication))
    """)
    public ResponseEntity<Object> update(@PathVariable long idClub,
                                         @RequestBody @Valid ClubRequestDto clubDto){
        clubOwnerService.update(idClub, clubDto);
        return ResponseEntity.ok(Map.of("message", "El club se actualizo con exito"));
    }

    @PostMapping("/{idClub}/owners")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @clubAuthorization.isOwnerOfClub(
                #idClub, authentication))
    """)
    public ResponseEntity<Object> addOwner(
            @PathVariable long idClub,
            @RequestBody @Valid OwnerRequestDto ownerDto)
            throws OwnerAlreadyExistsException {

        clubOwnerService.addOwner(idClub, ownerDto);
        return ResponseEntity.ok(
                Map.of("message", "Dueño agregado al club con éxito")
        );
    }

    @DeleteMapping("/{idClub}/owners/{idOwner}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and #idOwner == principal.id
            and @clubAuthorization.isOwnerOfClub(
                #idClub, authentication))
    """)
    public ResponseEntity<Object> deleteOwner(
            @PathVariable long idClub,
            @PathVariable long idOwner) {

        clubOwnerService.deleteOwner(idClub, idOwner);
        return ResponseEntity.ok(
                Map.of("message", "Dueño eliminado del club con éxito")
        );
    }

    @PatchMapping("/{idClub}/activate")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @clubAuthorization.isOwnerOfClub(
                #idClub, authentication))
    """)
    public ResponseEntity<Object> activate(
            @PathVariable long idClub) {

        clubOwnerService.activate(idClub);
        return ResponseEntity.ok(
                Map.of("message", "El club fue activado con éxito")
        );
    }

    @PatchMapping("/{idClub}/deactivate")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and @clubAuthorization.isOwnerOfClub(
                #idClub, authentication))
    """)
    public ResponseEntity<Object> deactivate(
            @PathVariable long idClub) {

        clubOwnerService.deactivate(idClub);
        return ResponseEntity.ok(
                Map.of("message", "El club fue desactivado con éxito")
        );
    }
}