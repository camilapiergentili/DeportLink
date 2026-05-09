package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.request.BranchRequestDto;
import com.deportlink.deportlink.dto.response.BranchResponseDto;
import com.deportlink.deportlink.service.BranchService;
import com.deportlink.deportlink.service.implementation.BranchServiceImplementation;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/branches")
@AllArgsConstructor
public class BranchController {

    private final BranchService branchService;

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
