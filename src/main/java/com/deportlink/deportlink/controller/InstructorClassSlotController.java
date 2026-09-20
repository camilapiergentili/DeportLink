package com.deportlink.deportlink.controller;
import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.usecase.classslot.*;
import com.deportlink.deportlink.dto.request.*;
import com.deportlink.deportlink.dto.response.ClassApiResponses.*;
import com.deportlink.deportlink.security.resolver.CurrentActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
@RestController
@RequestMapping("/api/instructor/class-slots")
@PreAuthorize("hasRole('INSTRUCTOR')")
@RequiredArgsConstructor
public class InstructorClassSlotController {
    private final CreateClassSlotUseCase create;
    private final GetClassSlotsByInstructorUseCase list;
    private final GetClassSlotDetailUseCase detail;
    private final PauseClassSlotUseCase pause;
    private final ReactivateClassSlotUseCase reactivate;
    private final AddPlayerToClassSlotUseCase add;
    private final RemovePlayerFromClassSlotUseCase remove;
    @GetMapping
    public List<Slot> list(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentActor Actor actor) {
        return list.execute(actor,actor.id()).stream().map(Slot::from).toList();
    }
    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ResponseEntity<Slot> create(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentActor Actor actor, @Valid @RequestBody ClassSlotRequestDto r) {
        var s = create.execute(actor,new CreateClassSlotCommand(actor.id(),r.courtId(),r.dayOfWeek(),
            r.startTime(),Duration.ofMinutes(r.durationMinutes()),r.level(),r.capacity()));
        return ResponseEntity.created(URI.create("/api/instructor/class-slots/"+s.id())).body(Slot.from(s));
    }
    @GetMapping("/{slotId}")
    public SlotDetail detail(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentActor Actor actor, @PathVariable @Positive Long slotId) {
        var d = detail.execute(actor,slotId);
        return new SlotDetail(Slot.from(d.slot()),d.players());
    }
    @PutMapping("/{slotId}/pause")
    public Slot pause(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentActor Actor actor, @PathVariable @Positive Long slotId) {
        return Slot.from(pause.execute(actor,slotId));
    }
    @PutMapping("/{slotId}/reactivate")
    public Slot reactivate(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentActor Actor actor, @PathVariable @Positive Long slotId) {
        return Slot.from(reactivate.execute(actor,slotId));
    }
    @PostMapping("/{slotId}/players")
    public Enrollment add(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentActor Actor actor, @PathVariable @Positive Long slotId,
            @Valid @RequestBody ClassPlayerRequestDto r) {
        return Enrollment.from(add.execute(actor,slotId,r.playerId()));
    }
    @DeleteMapping("/{slotId}/players/{playerId}")
    public Enrollment remove(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentActor Actor actor, @PathVariable @Positive Long slotId,
            @PathVariable @Positive Long playerId) {
        return Enrollment.from(remove.execute(actor,slotId,playerId));
    }
}
