package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.ClubRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.service.implementation.ClubServiceImplementation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("Club")
@RequiredArgsConstructor
public class ClubController {

    private final ClubServiceImplementation clubService;

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody ClubRequestDto clubDto){
        clubService.createClub(clubDto);
        return ResponseEntity.ok("El club fue creado con exito");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClubResponseDto> getById(@Valid @PathVariable long id){
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
    public ResponseEntity<Object> delete(@Valid @PathVariable long id){
        clubService.delete(id);
        return ResponseEntity.ok("Club eliminado con exito");
    }

    @PutMapping
    public ResponseEntity<Object> update(@Valid @PathVariable long id,
                                         @RequestBody ClubRequestDto clubDto) {
        clubService.update(id, clubDto);
        return ResponseEntity.ok(Map.of("message", "El club se elimino con exito"));
    }


}
