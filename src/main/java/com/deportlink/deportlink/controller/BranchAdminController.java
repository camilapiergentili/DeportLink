package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.response.BranchResponseDto;
import com.deportlink.deportlink.service.BranchAdminService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@AllArgsConstructor
public class BranchAdminController {

    private BranchAdminService branchAdminService;
    @GetMapping("/{id}")
    public ResponseEntity<BranchResponseDto> getById(@PathVariable long id){
        BranchResponseDto branchResponse = branchAdminService.getByIdResponse(id);
        return ResponseEntity.ok(branchResponse);
    }

    @GetMapping("/{idClub}/club")
    public ResponseEntity<List<BranchResponseDto>> getAll(@PathVariable long idClub){
        List<BranchResponseDto> branchesByClub = branchAdminService.getAll(idClub);
        return ResponseEntity.ok(branchesByClub);
    }
}
