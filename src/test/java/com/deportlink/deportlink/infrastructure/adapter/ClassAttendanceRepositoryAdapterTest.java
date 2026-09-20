package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.ClassAttendance;
import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort.AttendanceCounts;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.InstructorEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClassAttendanceRepositoryAdapterTest {

    @Autowired private ClassAttendanceRepositoryAdapter adapter;
    @Autowired private ClassSlotRepositoryAdapter classSlotAdapter;
    @Autowired private ClassSessionRepositoryAdapter classSessionAdapter;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private PlayerRepository playerRepository;

    private AdapterTestFixtures fixtures;
    private ClassSession session;
    private ClassSession otherSession;

    private static LocalDate nextThursday() {
        LocalDate day = LocalDate.now().plusDays(1);
        while (day.getDayOfWeek() != DayOfWeek.THURSDAY) day = day.plusDays(1);
        return day;
    }

    @BeforeEach
    void setUp() {
        fixtures = new AdapterTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, instructorRepository, playerRepository);
        CourtEntity court = fixtures.createCourt("A");
        InstructorEntity instructor = fixtures.createInstructor("A");
        ClassSlot slot = classSlotAdapter.save(ClassSlot.create(instructor.getId(), court.getId(), DayOfWeek.THURSDAY,
                LocalTime.of(15, 0), Duration.ofHours(1), Level.INTERMEDIO, 4));
        session = classSessionAdapter.save(ClassSession.create(slot.id(), nextThursday(), LocalTime.of(15, 0), Duration.ofHours(1)));
        otherSession = classSessionAdapter.save(ClassSession.create(slot.id(), nextThursday().plusWeeks(1), LocalTime.of(15, 0), Duration.ofHours(1)));
    }

    @Test
    void saveAll_generaUnaFilaPendingPorAlumno() {
        PlayerEntity p1 = fixtures.createPlayer("A");
        PlayerEntity p2 = fixtures.createPlayer("B");

        List<ClassAttendance> saved = adapter.saveAll(List.of(
                ClassAttendance.createPending(session.id(), p1.getId()),
                ClassAttendance.createPending(session.id(), p2.getId())
        ));

        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(a -> a.id() != null));
        assertEquals(2, adapter.findByClassSessionId(session.id()).size());
    }

    @Test
    void countByStatusForSessions_agregaCorrectamentePorSesionYEstado() {
        PlayerEntity p1 = fixtures.createPlayer("A");
        PlayerEntity p2 = fixtures.createPlayer("B");
        PlayerEntity p3 = fixtures.createPlayer("C");

        ClassAttendance a1 = adapter.save(ClassAttendance.createPending(session.id(), p1.getId()).confirm());
        adapter.save(ClassAttendance.createPending(session.id(), p2.getId())); // PENDING
        adapter.save(ClassAttendance.createPending(session.id(), p3.getId()).cancel()); // CANCELLED
        // Una asistencia en otra sesión — no debe mezclarse en los conteos de "session".
        adapter.save(ClassAttendance.createPending(otherSession.id(), p1.getId()));

        Map<Long, AttendanceCounts> counts = adapter.countByStatusForSessions(Set.of(session.id(), otherSession.id()));

        AttendanceCounts sessionCounts = counts.get(session.id());
        assertEquals(1, sessionCounts.pending());
        assertEquals(1, sessionCounts.confirmed());
        assertEquals(1, sessionCounts.cancelled());

        AttendanceCounts otherCounts = counts.get(otherSession.id());
        assertEquals(1, otherCounts.pending());
        assertEquals(0, otherCounts.confirmed());
        assertEquals(0, otherCounts.cancelled());
    }

    @Test
    void countByStatusForSessions_sesionSinAsistencias_noApareceEnElMapa() {
        Map<Long, AttendanceCounts> counts = adapter.countByStatusForSessions(Set.of(otherSession.id()));

        assertFalse(counts.containsKey(otherSession.id()));
    }
}
