package com.deportlink.deportlink.dto.response;
import com.deportlink.deportlink.domain.model.*;
import com.deportlink.deportlink.enums.*;
import com.deportlink.deportlink.application.usecase.classsession.*;
import com.deportlink.deportlink.application.usecase.classslot.GetClassSlotDetailUseCase;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;
import java.util.List;
/** HTTP projections: Duration becomes minutes; no persistence objects are exposed. */
public final class ClassApiResponses {
    private ClassApiResponses() {}
    public record Slot(Long id, Long instructorId, Long courtId, DayOfWeek dayOfWeek,
            @JsonFormat(pattern="HH:mm:ss") LocalTime startTime, long durationMinutes,
            Level level, int capacity, ActiveStatus status) {
        public static Slot from(ClassSlot s) {
            return new Slot(s.id(),s.instructorId(),s.courtId(),s.dayOfWeek(),s.startTime(),
                s.duration().toMinutes(),s.level(),s.capacity(),s.status());
        }
    }
    public record SlotDetail(Slot slot, List<GetClassSlotDetailUseCase.Member> players) {}
    public record Enrollment(Long id, Long classSlotId, Long playerId, boolean active) {
        public static Enrollment from(ClassEnrollment e) { return new Enrollment(e.id(),e.classSlotId(),e.playerId(),e.active()); }
    }
    public record Attendance(Long id, Long classSessionId, Long playerId, ClassAttendanceStatus status) {
        public static Attendance from(ClassAttendance a) { return new Attendance(a.id(),a.classSessionId(),a.playerId(),a.status()); }
    }
    public record SessionSummary(Long sessionId, Long classSlotId, LocalDate day,
            @JsonFormat(pattern="HH:mm:ss") LocalTime startTime, long durationMinutes,
            ClassSessionStatus status, Long courtId, String courtName, Level level, int capacity,
            long pendingCount, long confirmedCount, long cancelledCount, long occupancy, int availableSpots) {
        public static SessionSummary from(ClassSessionSummary s) {
            return new SessionSummary(s.sessionId(),s.classSlotId(),s.day(),s.startTime(),s.duration().toMinutes(),
                s.status(),s.courtId(),s.courtName(),s.level(),s.capacity(),s.pendingCount(),s.confirmedCount(),
                s.cancelledCount(),s.occupancy(),s.availableSpots());
        }
    }
    public record SessionDetail(SessionSummary summary, List<ClassAttendanceView> attendees) {}
}
