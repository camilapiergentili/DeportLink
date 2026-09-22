package com.deportlink.deportlink.controller;
import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.usecase.classattendance.*;
import com.deportlink.deportlink.dto.response.ClassApiResponses.Attendance;
import com.deportlink.deportlink.security.resolver.CurrentActor;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
@RestController
@RequestMapping("/api/instructor/class-attendances")
@PreAuthorize("hasRole('INSTRUCTOR')")
@RequiredArgsConstructor
public class InstructorClassAttendanceController {
    private final ConfirmAttendanceUseCase confirm;
    private final CancelAttendanceUseCase cancel;
    @PutMapping("/{attendanceId}/confirm")
    public Attendance confirm(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentActor Actor actor, @PathVariable @Positive Long attendanceId) {
        return Attendance.from(confirm.execute(actor,attendanceId));
    }
    @PutMapping("/{attendanceId}/cancel")
    public Attendance cancel(@io.swagger.v3.oas.annotations.Parameter(hidden=true) @CurrentActor Actor actor, @PathVariable @Positive Long attendanceId) {
        return Attendance.from(cancel.execute(actor,attendanceId));
    }
}
