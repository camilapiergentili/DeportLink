package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.enums.ActiveStatus;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nivel A (ver docs/class-management-stage-1c-persistence-design.md, sección 9.1): mapeo y
 * consultas del adapter contra H2 real (perfil "test") — no contra mocks. @SpringBootTest, mismo
 * patrón ya probado en CascadeDeletionRegressionTest (sin precedente de @DataJpaTest en el
 * proyecto — se prefirió el patrón ya validado antes que introducir uno nuevo sin evidencia).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClassSlotRepositoryAdapterTest {

    @Autowired private ClassSlotRepositoryAdapter adapter;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private PlayerRepository playerRepository;

    private AdapterTestFixtures fixtures;
    private InstructorEntity instructor;
    private CourtEntity court;

    @BeforeEach
    void setUp() {
        fixtures = new AdapterTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, instructorRepository, playerRepository);
        court = fixtures.createCourt("A");
        instructor = fixtures.createInstructor("A");
    }

    private ClassSlot newClassSlot() {
        return ClassSlot.create(instructor.getId(), court.getId(), DayOfWeek.THURSDAY,
                LocalTime.of(15, 0), Duration.ofHours(1), Level.INTERMEDIO, 4);
    }

    @Test
    void save_slotNuevo_loPersisteYAsignaId() {
        ClassSlot saved = adapter.save(newClassSlot());

        assertNotNull(saved.id());
        assertEquals(instructor.getId(), saved.instructorId());
        assertEquals(court.getId(), saved.courtId());
        assertEquals(DayOfWeek.THURSDAY, saved.dayOfWeek());
        assertEquals(Level.INTERMEDIO, saved.level());
        assertEquals(4, saved.capacity());
        assertEquals(ActiveStatus.ACTIVE, saved.status());
    }

    @Test
    void save_actualizacion_persisteElNuevoEstado() {
        ClassSlot saved = adapter.save(newClassSlot());

        ClassSlot paused = adapter.save(saved.pause());

        assertEquals(ActiveStatus.INACTIVE, paused.status());
        ClassSlot reloaded = adapter.findById(saved.id()).orElseThrow();
        assertEquals(ActiveStatus.INACTIVE, reloaded.status());
    }

    @Test
    void findByIdForUpdate_existente_loDevuelve() {
        ClassSlot saved = adapter.save(newClassSlot());

        assertTrue(adapter.findByIdForUpdate(saved.id()).isPresent());
    }

    @Test
    void findByIdForUpdate_inexistente_devuelveVacio() {
        assertTrue(adapter.findByIdForUpdate(999_999L).isEmpty());
    }

    @Test
    void findAllByInstructorId_devuelveSoloLosDeEseInstructor() {
        adapter.save(newClassSlot());
        InstructorEntity otherInstructor = fixtures.createInstructor("B");
        adapter.save(ClassSlot.create(otherInstructor.getId(), court.getId(), DayOfWeek.FRIDAY,
                LocalTime.of(10, 0), Duration.ofHours(1), Level.AVANZADO, 2));

        List<ClassSlot> result = adapter.findAllByInstructorId(instructor.getId());

        assertEquals(1, result.size());
        assertEquals(instructor.getId(), result.get(0).instructorId());
    }

    @Test
    void findAllByIds_batchDeVariosSlots_losDevuelveTodosEnUnaSolaConsulta() {
        ClassSlot s1 = adapter.save(newClassSlot());
        ClassSlot s2 = adapter.save(ClassSlot.create(instructor.getId(), court.getId(), DayOfWeek.FRIDAY,
                LocalTime.of(10, 0), Duration.ofHours(1), Level.AVANZADO, 2));

        List<ClassSlot> result = adapter.findAllByIds(Set.of(s1.id(), s2.id(), 999_999L));

        assertEquals(2, result.size());
    }

    @Test
    void findAllByIds_setVacio_devuelveListaVaciaSinConsultar() {
        assertTrue(adapter.findAllByIds(Set.of()).isEmpty());
    }
}
