package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.enums.ClassSessionStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.InstructorEntity;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirma en particular la decisión cerrada de la sección 3.4 del diseño de 1C: ClassSession NO
 * persiste courtId — el record de dominio devuelto por el adapter nunca lo expone (no existe ese
 * campo en absoluto), y la cancha se resuelve siempre a través de ClassSlot.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClassSessionRepositoryAdapterTest {

    @Autowired private ClassSessionRepositoryAdapter adapter;
    @Autowired private ClassSlotRepositoryAdapter classSlotAdapter;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private PlayerRepository playerRepository;

    private ClassSlot slot;

    private static LocalDate nextThursday() {
        LocalDate day = LocalDate.now().plusDays(1);
        while (day.getDayOfWeek() != DayOfWeek.THURSDAY) day = day.plusDays(1);
        return day;
    }

    @BeforeEach
    void setUp() {
        AdapterTestFixtures fixtures = new AdapterTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, instructorRepository, playerRepository);
        CourtEntity court = fixtures.createCourt("A");
        InstructorEntity instructor = fixtures.createInstructor("A");
        slot = classSlotAdapter.save(ClassSlot.create(instructor.getId(), court.getId(), DayOfWeek.THURSDAY,
                LocalTime.of(15, 0), Duration.ofHours(1), Level.INTERMEDIO, 4));
    }

    @Test
    void save_sesionNueva_laPersisteConEstadoScheduled() {
        ClassSession session = ClassSession.create(slot.id(), nextThursday(), LocalTime.of(15, 0), Duration.ofHours(1));

        ClassSession saved = adapter.save(session);

        assertNotNull(saved.id());
        assertEquals(slot.id(), saved.classSlotId());
        assertEquals(ClassSessionStatus.SCHEDULED, saved.status());
    }

    @Test
    void existsByClassSlotIdAndDay_sesionYaCreada_devuelveTrue() {
        LocalDate day = nextThursday();
        adapter.save(ClassSession.create(slot.id(), day, LocalTime.of(15, 0), Duration.ofHours(1)));

        assertTrue(adapter.existsByClassSlotIdAndDay(slot.id(), day));
        assertFalse(adapter.existsByClassSlotIdAndDay(slot.id(), day.plusWeeks(1)));
    }

    @Test
    void findByInstructor_filtraPorInstructorYRangoDeFechas() {
        LocalDate week1 = nextThursday();
        LocalDate week2 = week1.plusWeeks(1);
        LocalDate week3 = week1.plusWeeks(2);
        adapter.save(ClassSession.create(slot.id(), week1, LocalTime.of(15, 0), Duration.ofHours(1)));
        adapter.save(ClassSession.create(slot.id(), week2, LocalTime.of(15, 0), Duration.ofHours(1)));
        adapter.save(ClassSession.create(slot.id(), week3, LocalTime.of(15, 0), Duration.ofHours(1)));

        // Rango acotado [week1, week2] — no debe incluir week3.
        List<ClassSession> result = adapter.findByInstructor(slot.instructorId(), week1, week2);
        assertEquals(2, result.size());
        assertTrue(result.stream().map(ClassSession::day).allMatch(d -> !d.isAfter(week2)));

        // Sin límite superior (null) — debe incluir las tres.
        List<ClassSession> unbounded = adapter.findByInstructor(slot.instructorId(), week1, null);
        assertEquals(3, unbounded.size());
    }
}
