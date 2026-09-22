package com.deportlink.deportlink.controller;
import com.deportlink.deportlink.application.usecase.instructor.InstructorLookupUseCase;
import com.deportlink.deportlink.application.port.out.InstructorLookupPort.*;
import com.deportlink.deportlink.dto.request.PlayerLookupRequestDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
@RestController
@RequestMapping("/api/instructor")
@PreAuthorize("hasRole('INSTRUCTOR')")
@RequiredArgsConstructor
public class InstructorLookupController {
    private final InstructorLookupUseCase lookup;
    @GetMapping("/courts")
    public CourtPage courts(@RequestParam(required=false) @Positive Long branchId,
            @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(50) int size) {
        return lookup.courts(branchId,page,size);
    }
    @PostMapping("/player-lookup")
    public PlayerOption player(@Valid @RequestBody PlayerLookupRequestDto r) {
        return lookup.player(r.email());
    }
}
