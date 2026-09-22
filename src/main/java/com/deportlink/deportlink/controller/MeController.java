package com.deportlink.deportlink.controller;
import com.deportlink.deportlink.application.usecase.instructor.InstructorAccountUseCase;
import com.deportlink.deportlink.security.resolver.CurrentUserId;
import com.deportlink.deportlink.model.Rol;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
@RestController
@RequiredArgsConstructor
public class MeController {
    private final InstructorAccountUseCase accounts;
    public record Identity(Long id, String firstName, String lastName, Rol role) {}
    @GetMapping("/api/me")
    public Identity me(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentUserId Long id) {
        var p = accounts.user(id);
        return new Identity(p.id(), p.firstName(), p.lastName(), p.role());
    }
}
