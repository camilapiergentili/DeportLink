package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.ClassEnrollment;
import com.deportlink.deportlink.domain.model.ClassSlot;
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
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClassEnrollmentRepositoryAdapterTest {

    @Autowired private ClassEnrollmentRepositoryAdapter adapter;
    @Autowired private ClassSlotRepositoryAdapter classSlotAdapter;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private PlayerRepository playerRepository;

    private ClassSlot slot;
    private PlayerEntity player;

    @BeforeEach
    void setUp() {
        AdapterTestFixtures fixtures = new AdapterTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, instructorRepository, playerRepository);
        CourtEntity court = fixtures.createCourt("A");
        InstructorEntity instructor = fixtures.createInstructor("A");
        player = fixtures.createPlayer("A");
        slot = classSlotAdapter.save(ClassSlot.create(instructor.getId(), court.getId(), DayOfWeek.THURSDAY,
                LocalTime.of(15, 0), Duration.ofHours(1), Level.INTERMEDIO, 4));
    }

    @Test
    void save_nuevo_loPersisteActivo() {
        ClassEnrollment saved = adapter.save(ClassEnrollment.create(slot.id(), player.getId()));

        assertNotNull(saved.id());
        assertTrue(saved.active());
    }

    @Test
    void findByClassSlotIdAndPlayerId_existente_loEncuentra() {
        adapter.save(ClassEnrollment.create(slot.id(), player.getId()));

        Optional<ClassEnrollment> found = adapter.findByClassSlotIdAndPlayerId(slot.id(), player.getId());

        assertTrue(found.isPresent());
    }

    @Test
    void reactivacion_conservaElIdEnVezDeCrearUnaFilaNueva() {
        ClassEnrollment created = adapter.save(ClassEnrollment.create(slot.id(), player.getId()));
        Long originalId = created.id();
        ClassEnrollment deactivated = adapter.save(created.deactivate());
        assertFalse(deactivated.active());

        // AddPlayerToClassSlotUseCase (1B) reactiva la fila existente en vez de crear una nueva —
        // este test confirma que el adapter persiste esa reactivación conservando el id.
        ClassEnrollment reactivated = adapter.save(deactivated.activate());

        assertEquals(originalId, reactivated.id());
        assertTrue(reactivated.active());
        // Solo debe existir UNA fila para este (slot, player) — no una segunda por la reactivación.
        assertEquals(1, adapter.countActiveByClassSlotId(slot.id()));
    }

    @Test
    void countActiveByClassSlotId_soloCuentaLosActivos() {
        PlayerEntity player2 = new AdapterTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, instructorRepository, playerRepository).createPlayer("B");

        ClassEnrollment active = adapter.save(ClassEnrollment.create(slot.id(), player.getId()));
        ClassEnrollment toDeactivate = adapter.save(ClassEnrollment.create(slot.id(), player2.getId()));
        adapter.save(toDeactivate.deactivate());

        assertEquals(1, adapter.countActiveByClassSlotId(slot.id()));
        assertEquals(1, adapter.findActiveByClassSlotId(slot.id()).size());
    }
}
