package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.ClubRequestDto;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;
import com.deportlink.deportlink.service.ClubOwnerService;
import com.deportlink.deportlink.service.ClubService;
import com.deportlink.deportlink.service.implementation.ClubServiceImplementation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;

    @GetMapping("/{id}")
    public ResponseEntity<ClubResponseDto> getById(@PathVariable long id){
        ClubResponseDto clubDto = clubService.getByIdResponse(id);
        return ResponseEntity.ok(clubDto);
    }

    @GetMapping("/approved")
    public ResponseEntity<Page<ClubResponseDto>> getApproved(Pageable pageable){
        Page<ClubResponseDto> clubs = clubService.getByActiveAndApprovedPaginated(pageable);
        if(clubs.isEmpty()){
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(clubs);
    }

    @GetMapping("/all")
    public ResponseEntity<Page<ClubResponseDto>> getAll(Pageable pageable){
        Page<ClubResponseDto> allClubs = clubService.getAllPaginated(pageable);
        if(allClubs.isEmpty()){
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(allClubs);
    }

}
