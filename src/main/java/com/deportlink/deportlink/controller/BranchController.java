package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.BranchRequestDto;
import com.deportlink.deportlink.dto.response.BranchResponseDto;
import com.deportlink.deportlink.persistence.repository.BranchRepository;
import com.deportlink.deportlink.service.implementation.BranchServiceImplementation;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@AllArgsConstructor
public class BranchController {

    private final BranchServiceImplementation branchService;

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody @Valid BranchRequestDto branchDto){
        branchService.create(branchDto);
        return ResponseEntity.status(HttpStatus.CREATED).body("Sucursal creada con exito");
    }

    @GetMapping("/{id}")
    public ResponseEntity<BranchResponseDto> getById(@PathVariable long id){
        BranchResponseDto branchResponse = branchService.getByIdResponse(id);
        return ResponseEntity.ok(branchResponse);
    }

    @GetMapping("/{idClub}/club")
    public ResponseEntity<List<BranchResponseDto>> getAll(@PathVariable long idClub){
        List<BranchResponseDto> branchesByClub = branchService.getAll(idClub);
        return ResponseEntity.ok(branchesByClub);
    }

    @GetMapping("/{idClub}/active-approved")
    public ResponseEntity<List<BranchResponseDto>> getApprovedAndActiveByClub(@PathVariable long idClub){
        List<BranchResponseDto> branches =  branchService.getAllActiveAndApproved(idClub);
        return ResponseEntity.ok(branches);
    }

    @GetMapping("/{idBranch}/approved")
    public ResponseEntity<BranchResponseDto> getApprovedById(@PathVariable long idBranch){
        BranchResponseDto branch = branchService.getByIdApproved(idBranch);
        return ResponseEntity.ok(branch);
    }


}
