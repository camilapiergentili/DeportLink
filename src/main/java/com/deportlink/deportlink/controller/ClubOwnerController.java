package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.port.out.OwnerGateway;
import com.deportlink.deportlink.application.usecase.club.*;
import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.dto.request.ClubRequestDto;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clubs/owner")
@RequiredArgsConstructor
public class ClubOwnerController {

    private final CreateClubUseCase createClubUseCase;
    private final DeleteClubUseCase deleteClubUseCase;
    private final UpdateClubUseCase updateClubUseCase;
    private final AddOwnerToClubUseCase addOwnerToClubUseCase;
    private final RemoveOwnerFromClubUseCase removeOwnerFromClubUseCase;
    private final ActivateClubUseCase activateClubUseCase;
    private final DeactivateClubUseCase deactivateClubUseCase;

    @PostMapping
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and #clubDto.ownerIds.contains(principal.id))
    """)
    public ResponseEntity<ClubResponseDto> create(@RequestBody @Valid ClubRequestDto clubDto) {
        Club club = createClubUseCase.execute(
                clubDto.getName(),
                clubDto.getLegalName(),
                clubDto.getCuit(),
                clubDto.getClubType(),
                clubDto.getOwnerIds()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(club));
    }

    @DeleteMapping("/{idClub}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @clubAuthorization.isOwnerOfClub(#idClub, authentication))
    """)
    public ResponseEntity<Object> delete(@PathVariable long idClub) {
        deleteClubUseCase.execute(idClub);
        return ResponseEntity.ok("Club eliminado con éxito");
    }

    @PutMapping("/{idClub}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @clubAuthorization.isOwnerOfClub(#idClub, authentication))
    """)
    public ResponseEntity<Object> update(@PathVariable long idClub,
                                         @RequestBody @Valid ClubRequestDto clubDto) {
        updateClubUseCase.execute(idClub,
                clubDto.getName(),
                clubDto.getLegalName(),
                clubDto.getCuit(),
                clubDto.getClubType());
        return ResponseEntity.ok(Map.of("message", "El club se actualizó con éxito"));
    }

    @PostMapping("/{idClub}/owners")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @clubAuthorization.isOwnerOfClub(#idClub, authentication))
    """)
    public ResponseEntity<Object> addOwner(@PathVariable long idClub,
                                           @RequestBody @Valid OwnerRequestDto ownerDto) {
        addOwnerToClubUseCase.execute(idClub, toOwnerCommand(ownerDto));
        return ResponseEntity.ok(Map.of("message", "Dueño agregado al club con éxito"));
    }

    @DeleteMapping("/{idClub}/owners/{idOwner}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER')
            and #idOwner == principal.id
            and @clubAuthorization.isOwnerOfClub(#idClub, authentication))
    """)
    public ResponseEntity<Object> deleteOwner(@PathVariable long idClub,
                                              @PathVariable long idOwner) {
        removeOwnerFromClubUseCase.execute(idClub, idOwner);
        return ResponseEntity.ok(Map.of("message", "Dueño eliminado del club con éxito"));
    }

    @PatchMapping("/{idClub}/activate")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @clubAuthorization.isOwnerOfClub(#idClub, authentication))
    """)
    public ResponseEntity<Object> activate(@PathVariable long idClub) {
        activateClubUseCase.execute(idClub);
        return ResponseEntity.ok(Map.of("message", "El club fue activado con éxito"));
    }

    @PatchMapping("/{idClub}/deactivate")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @clubAuthorization.isOwnerOfClub(#idClub, authentication))
    """)
    public ResponseEntity<Object> deactivate(@PathVariable long idClub) {
        deactivateClubUseCase.execute(idClub);
        return ResponseEntity.ok(Map.of("message", "El club fue desactivado con éxito"));
    }

    private ClubResponseDto toResponse(Club club) {
        ClubResponseDto dto = new ClubResponseDto();
        dto.setId(club.id());
        dto.setName(club.name());
        dto.setLegalName(club.legalName());
        dto.setCuit(club.cuit());
        dto.setClubType(club.clubType());
        dto.setVerificationStatus(club.verificationStatus());
        dto.setActiveStatus(club.activeStatus());
        return dto;
    }

    private OwnerGateway.OwnerCommand toOwnerCommand(OwnerRequestDto dto) {
        return new OwnerGateway.OwnerCommand(
                dto.getFirstName(),
                dto.getLastName(),
                dto.getEmail(),
                dto.getPassword(),
                dto.getPhone(),
                dto.getDni(),
                dto.getCuil(),
                dto.getDateOfBirth()
        );
    }
}