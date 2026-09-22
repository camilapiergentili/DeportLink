package com.deportlink.deportlink.controller;
import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.usecase.classsession.*;
import com.deportlink.deportlink.dto.response.ClassApiResponses.*;
import com.deportlink.deportlink.security.resolver.CurrentActor;
import com.deportlink.deportlink.exception.InvalidClassRequestException;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
@RestController
@RequestMapping("/api/instructor/class-sessions")
@PreAuthorize("hasRole('INSTRUCTOR')")
@RequiredArgsConstructor
public class InstructorClassSessionController {
    private final GetClassSessionsByInstructorUseCase list;
    private final GetClassSessionDetailUseCase detail;
    private final Clock clock;
    @GetMapping
    public List<SessionSummary> list(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentActor Actor actor,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(clock) : from;
        LocalDate end;
        try { end = to == null ? start.plusDays(27) : to; }
        catch (DateTimeException ex) { throw new InvalidClassRequestException("Fecha fuera de rango"); }
        long days = ChronoUnit.DAYS.between(start,end);
        if (days < 0 || days >= 93) throw new InvalidClassRequestException("El rango debe tener entre 1 y 93 días");
        return list.execute(actor,actor.id(),start,end).stream().map(SessionSummary::from).toList();
    }
    @GetMapping("/{sessionId}")
    public SessionDetail detail(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentActor Actor actor, @PathVariable @Positive Long sessionId) {
        var d = detail.execute(actor,sessionId);
        return new SessionDetail(SessionSummary.from(d.summary()), d.attendees());
    }
}
