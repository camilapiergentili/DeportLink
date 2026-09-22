package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.ClassSlot;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que el JOIN (CourtEntity.classSlots) resuelve correctamente la cancha de un ClassSlot
 * — la garantía de que efectivamente bloquea la fila bajo carga concurrente real la prueba
 * ReservationVsClassSessionConcurrencyTest (Nivel C, Testcontainers), no este test con H2 (ver
 * docs/class-management-stage-1c-persistence-design.md, sección 7.2).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClassSlotCourtGatewayAdapterTest {

    @Autowired private ClassSlotCourtGatewayAdapter gateway;
    @Autowired private ClassSlotRepositoryAdapter classSlotAdapter;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private PlayerRepository playerRepository;

    @Test
    void findCourtIdByClassSlotForUpdate_slotExistente_devuelveElCourtIdCorrecto() {
        AdapterTestFixtures fixtures = new AdapterTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, instructorRepository, playerRepository);
        CourtEntity court = fixtures.createCourt("A");
        InstructorEntity instructor = fixtures.createInstructor("A");
        ClassSlot slot = classSlotAdapter.save(ClassSlot.create(instructor.getId(), court.getId(), DayOfWeek.THURSDAY,
                LocalTime.of(15, 0), Duration.ofHours(1), Level.INTERMEDIO, 4));

        Optional<Long> courtId = gateway.findCourtIdByClassSlotForUpdate(slot.id());

        assertTrue(courtId.isPresent());
        assertEquals(court.getId(), courtId.get());
    }

    @Test
    void findCourtIdByClassSlotForUpdate_slotInexistente_devuelveVacio() {
        assertTrue(gateway.findCourtIdByClassSlotForUpdate(999_999L).isEmpty());
    }
}
