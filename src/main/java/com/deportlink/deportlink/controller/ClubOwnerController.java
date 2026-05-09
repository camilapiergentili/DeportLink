package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;
import com.deportlink.deportlink.service.ClubOwnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubOwnerController {

    private final ClubOwnerService clubOwnerService;

    @PostMapping("/{idClub}/owners")
    public ResponseEntity<Object> addOwner(@PathVariable long idClub,
                                           @RequestBody @Valid OwnerRequestDto ownerDto) throws OwnerAlreadyExistsException, OwnerAlreadyExistsException {
        clubOwnerService.addOwner(idClub, ownerDto);
        return ResponseEntity.ok(Map.of("message", "Owner agregado a club con exito"));
    }

    @DeleteMapping("/{idClub}/owners/{idOwner}")
    public ResponseEntity<Object> deleteOwner(@PathVariable long idClub,
                                              @PathVariable long idOwner){
        clubOwnerService.deleteOwner(idClub, idOwner);
        return ResponseEntity.ok("Owner eliminado del club con exito");
    }

    @PatchMapping("/{idClub}/activate")
    public ResponseEntity<Object> activate(@RequestParam long idOwner,
                                           @PathVariable long idClub){
        clubOwnerService.activate(idOwner, idClub);
        return ResponseEntity.ok(Map.of("message", "El club fue activado con exito"));
    }

    @PatchMapping("/{idClub}/desactivate")
    public ResponseEntity<Object> desactivate(@RequestParam long idOwner,
                                              @PathVariable long idClub){
        clubOwnerService.deactivate(idOwner, idClub);
        return ResponseEntity.ok(Map.of("message", "El club fue desactivado con exito"));
    }
}