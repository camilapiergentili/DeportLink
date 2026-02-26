package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.ClubRequestDto;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;
import com.deportlink.deportlink.service.implementation.ClubServiceImplementation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubServiceImplementation clubService;

    @PostMapping
    public ResponseEntity<ClubResponseDto> create(@RequestBody @Valid ClubRequestDto clubDto){
        ClubResponseDto clubResponse = clubService.createClub(clubDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clubResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClubResponseDto> getById(@PathVariable long id){
        ClubResponseDto clubDto = clubService.getByIdResponse(id);
        return ResponseEntity.ok(clubDto);
    }

    @GetMapping("/approved")
    public ResponseEntity<List<ClubResponseDto>> getApproved(){
        List<ClubResponseDto> listClubs = clubService.getAllClubsActiveAndApproved();

        if(listClubs.isEmpty()){
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(listClubs);
    }

    @GetMapping("/all")
    public ResponseEntity<List<ClubResponseDto>> getAll(){
        List<ClubResponseDto> allClubs = clubService.getAll();

        if(allClubs.isEmpty()){
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(allClubs);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable long id){
        clubService.delete(id);
        return ResponseEntity.ok("Club eliminado con exito");
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable long id,
                                         @RequestBody @Valid ClubRequestDto clubDto) {
        clubService.update(id, clubDto);
        return ResponseEntity.ok(Map.of("message", "El club se actualizo con exito"));
    }

    @PostMapping("/{idClub}/owners")
    public ResponseEntity<Object> addOwnerToClub(@PathVariable long idClub,
                                                 @RequestBody @Valid OwnerRequestDto ownerDto) throws OwnerAlreadyExistsException {
        clubService.addOwnerToClub(idClub, ownerDto);
        return ResponseEntity.ok(Map.of("message", "Owner agregado a club con exito"));
    }

    @DeleteMapping("/{idClub}/owners/{idOwner}")
    public ResponseEntity<Object> deleteOwnerToClub(@PathVariable long idClub,
                                                    @PathVariable long idOwner){
        clubService.deleteOwnerToClub(idClub, idOwner);
        return ResponseEntity.ok("Owner eliminado del club con exito");
    }

    @PatchMapping("/{idClub}/activate")
    public ResponseEntity<Object> activate(@RequestParam long idOwner,
                                           @PathVariable long idClub){
        clubService.activateClub(idOwner, idClub);
        return ResponseEntity.ok(Map.of("message", "El club fue activado con exito"));
    }

    @PatchMapping("/{idClub}/desactivate")
    public ResponseEntity<Object> desactivate(@RequestParam long idOwner,
                                           @PathVariable long idClub){
        clubService.deactivateClub(idOwner, idClub);
        return ResponseEntity.ok(Map.of("message", "El club fue activado con exito"));
    }

}
