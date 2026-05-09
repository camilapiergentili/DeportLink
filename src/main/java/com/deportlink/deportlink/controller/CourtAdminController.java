package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.service.CourtAdminService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courts")
@AllArgsConstructor
public class CourtAdminController {

    private CourtAdminService courtAdminService;

    @GetMapping("/branch/{idBranch}")
    public ResponseEntity<List<CourtResponseDto>> getAllByBranch(@PathVariable long idBranch) {
        return ResponseEntity.ok(courtAdminService.getAllBranch(idBranch));
    }

    @GetMapping
    public ResponseEntity<List<CourtResponseDto>> getAll(){
        return ResponseEntity.ok(courtAdminService.getAll());
    }
}
