package com.deportlink.deportlink.integration;

import com.deportlink.deportlink.application.usecase.classslot.*;
import com.deportlink.deportlink.application.usecase.classsession.MaintainClassSlotScheduleUseCase;
import com.deportlink.deportlink.application.usecase.classattendance.ConfirmAttendanceUseCase;
import com.deportlink.deportlink.application.usecase.reservation.*;
import com.deportlink.deportlink.domain.model.*;
import com.deportlink.deportlink.domain.port.out.*;
import com.deportlink.deportlink.enums.*;
import com.deportlink.deportlink.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Real transactions and the Flyway schema, with each fixture isolated by court and slot IDs. */
class ClassRecurrenceIntegrationTest extends OccupancyLifecycleFixture {
    @Autowired private CreateClassSlotUseCase createSlot;
    @Autowired private MaintainClassSlotScheduleUseCase maintain;
    @Autowired private PauseClassSlotUseCase pause;
    @Autowired private ReactivateClassSlotUseCase reactivate;
    @Autowired private AddPlayerToClassSlotUseCase add;
    @Autowired private RemovePlayerFromClassSlotUseCase remove;
    @Autowired private ConfirmAttendanceUseCase confirm;
    @Autowired private BookReservationUseCase book;
    @Autowired private RescheduleReservationUseCase reschedule;
    @Autowired private GetAvailableSlotsUseCase available;
    @Autowired private ClassSessionRepositoryPort sessions;
    @Autowired private ClassAttendanceRepositoryPort attendances;

    private CreateClassSlotCommand command(LocalTime time) {
        Long instructorId = jdbc.queryForObject("SELECT instructor_id FROM class_slot WHERE id = ?", Long.class, slotId);
        return new CreateClassSlotCommand(instructorId, courtId, day.getDayOfWeek(), time,
                Duration.ofHours(1), Level.INTERMEDIO, 4);
    }
    private long sessionCount(Long id) {
        return count("SELECT COUNT(*) FROM class_session WHERE class_slot_id = ?", id);
    }
    private List<Long> attendanceIds(Long id) {
        return jdbc.queryForList("SELECT a.id FROM class_attendance a JOIN class_session s ON s.id=a.class_session_id WHERE s.class_slot_id=? ORDER BY a.id", Long.class, id);
    }

    @Test void creationImmediatelyGeneratesFourWeeksAndRepeatedMaintenancePreservesResponses() {
        var slot = createSlot.execute(admin, command(LocalTime.of(16, 0)));
        assertEquals(4L, sessionCount(slot.id()));
        add.execute(admin, slot.id(), playerId);
        var ids = attendanceIds(slot.id());
        assertEquals(4, ids.size());
        confirm.execute(admin, ids.getFirst());
        assertEquals(0, maintain.execute(slot.id()));
        assertEquals(ids, attendanceIds(slot.id()));
        assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM class_attendance WHERE id=?", String.class, ids.getFirst()));
        var dates = jdbc.queryForList("SELECT session_date FROM class_session WHERE class_slot_id=? ORDER BY session_date", java.sql.Date.class, slot.id());
        for (int i = 0; i < 4; i++) {
            assertEquals(day.getDayOfWeek(), dates.get(i).toLocalDate().getDayOfWeek());
            assertEquals(dates.getFirst().toLocalDate().plusWeeks(i), dates.get(i).toLocalDate());
        }
    }

    @Test void restartRecoveryFillsExistingScheduleButPauseDoesNotDeleteItsSessions() {
        assertEquals(4, maintain.execute(slotId));
        var ids = attendanceIds(slotId);
        pause.execute(admin, slotId);
        assertEquals(0, maintain.execute(slotId));
        assertEquals(4L, sessionCount(slotId));
        assertEquals(ids, attendanceIds(slotId));
        reactivate.execute(admin, slotId); // own occupancy is not a conflict
        assertEquals(4L, sessionCount(slotId));
        assertEquals(ids, attendanceIds(slotId));
    }

    @Test void weeklyScheduleBlocksBookingAndAvailabilityBeyondFourWeeks() {
        LocalDate farDay = day.plusWeeks(20);
        assertEquals(0L, sessionCount(slotId)); // protection comes from recurrence, not a session
        assertFalse(available.execute(courtId, farDay).contains(destinationTime));
        assertThrows(SlotNotAvailableException.class,
                () -> book.execute(courtId, playerId, farDay, destinationTime));
        var old = book.execute(courtId, playerId, farDay, originalTime);
        assertThrows(SlotNotAvailableException.class,
                () -> reschedule.execute(old.id(), playerId, farDay, destinationTime));
        assertEquals("RESERVADO", jdbc.queryForObject("SELECT status FROM reservation WHERE id=?", String.class, old.id()));
    }

    @Test void existingReservationMonthsAheadRejectsNewWeeklyScheduleAtomically() {
        var reservation = book.execute(courtId, playerId, day.plusWeeks(30), originalTime);
        assertThrows(CourtSlotOccupiedException.class, () -> createSlot.execute(admin, command(originalTime)));
        assertEquals(1L, count("SELECT COUNT(*) FROM class_slot WHERE court_id=?", courtId));
        assertEquals(0L, sessionCount(slotId));
        assertEquals(1L, count("SELECT COUNT(*) FROM court_occupancy WHERE source_type='RESERVATION' AND source_id=?", reservation.id()));
    }

    @Test void reactivationRejectsBookingsMadeWhilePausedWithoutLosingThem() {
        pause.execute(admin, slotId);
        var reservation = book.execute(courtId, playerId, day.plusWeeks(30), destinationTime);
        assertThrows(CourtSlotOccupiedException.class, () -> reactivate.execute(admin, slotId));
        assertEquals("INACTIVE", jdbc.queryForObject("SELECT active_status FROM class_slot WHERE id=?", String.class, slotId));
        assertEquals("RESERVADO", jdbc.queryForObject("SELECT status FROM reservation WHERE id=?", String.class, reservation.id()));
    }

    @Test void duplicateWeeklyScheduleIsRejectedBeforeSaving() {
        assertThrows(CourtSlotOccupiedException.class, () -> createSlot.execute(admin, command(destinationTime)));
        assertEquals(1L, count("SELECT COUNT(*) FROM class_slot WHERE court_id=?", courtId));
    }

    @Test void groupChangesOnlyAffectFutureSessionsAndRejoinReusesRows() {
        var slot = createSlot.execute(admin, command(LocalTime.of(16, 0)));
        add.execute(admin, slot.id(), playerId);
        var ids = attendanceIds(slot.id());
        var past = sessions.save(new ClassSession(null, slot.id(), LocalDate.now().minusWeeks(1),
                LocalTime.of(16, 0), Duration.ofHours(1), ClassSessionStatus.SCHEDULED));
        var historical = attendances.save(ClassAttendance.createPending(past.id(), playerId).confirm());
        confirm.execute(admin, ids.getFirst());

        remove.execute(admin, slot.id(), playerId);
        for (Long id : ids) {
            assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM class_attendance WHERE id=?", String.class, id));
        }
        assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM class_attendance WHERE id=?", String.class, historical.id()));
        assertThrows(ClassEnrollmentNotFoundException.class, () -> confirm.execute(admin, ids.getFirst()));

        add.execute(admin, slot.id(), playerId);
        for (Long id : ids) {
            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM class_attendance WHERE id=?", String.class, id));
        }
        assertEquals(5, attendanceIds(slot.id()).size()); // four future + unchanged history
        assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM class_attendance WHERE id=?", String.class, historical.id()));
    }

    @Test void addingAfterAutomaticCreationOnlyCopiesActiveGroupToNextSessions() {
        // Existing slot has one member; creating sessions without changing the group copies it.
        assertEquals(4, maintain.execute(slotId));
        assertEquals(4, attendanceIds(slotId).size());
        remove.execute(admin, slotId, playerId);
        // All current rows stay for history, cancelled.
        assertEquals(4, attendanceIds(slotId).size());
        assertEquals(0L, count("SELECT COUNT(*) FROM class_attendance a JOIN class_session s ON s.id=a.class_session_id WHERE s.class_slot_id=? AND a.status<>'CANCELLED'", slotId));
    }
}
