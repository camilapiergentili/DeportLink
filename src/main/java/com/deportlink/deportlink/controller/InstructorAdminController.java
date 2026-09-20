package com.deportlink.deportlink.controller;
import com.deportlink.deportlink.application.usecase.instructor.InstructorAccountUseCase;
import com.deportlink.deportlink.application.port.out.InstructorAccountPort.Profile;
import com.deportlink.deportlink.dto.request.InstructorRequestDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
@RestController
@RequestMapping("/api/admin/instructors")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class InstructorAdminController {
    private final InstructorAccountUseCase accounts;
    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ResponseEntity<Profile> register(@Valid @RequestBody InstructorRequestDto r) {
        var p = accounts.register(r.firstName(),r.lastName(),r.email(),r.password(),r.phone());
        return ResponseEntity.created(URI.create("/api/admin/instructors/"+p.id())).body(p);
    }
    @GetMapping("/{id}")
    public Profile get(@PathVariable @Positive Long id) { return accounts.instructor(id); }
}
