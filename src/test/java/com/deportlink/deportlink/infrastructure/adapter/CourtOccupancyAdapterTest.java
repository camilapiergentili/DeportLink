package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourtOccupancyAdapterTest {

    @Autowired private CourtOccupancyAdapter adapter;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private PlayerRepository playerRepository;

    private CourtEntity court;
    private static final LocalDate DAY = LocalDate.now().plusDays(5);
    private static final LocalTime START_TIME = LocalTime.of(15, 0);

    @BeforeEach
    void setUp() {
        AdapterTestFixtures fixtures = new AdapterTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, instructorRepository, playerRepository);
        court = fixtures.createCourt("A");
    }

    @Test
    void existsOccupancy_sinRegistros_esFalse() {
        assertFalse(adapter.existsOccupancy(court.getId(), DAY, START_TIME));
    }

    @Test
    void registerForReservation_luegoExistsOccupancy_esTrue() {
        adapter.registerForReservation(1L, court.getId(), DAY, START_TIME);

        assertTrue(adapter.existsOccupancy(court.getId(), DAY, START_TIME));
    }

    @Test
    void registerForClassSession_luegoExistsOccupancy_esTrue() {
        adapter.registerForClassSession(1L, court.getId(), DAY, START_TIME);

        assertTrue(adapter.existsOccupancy(court.getId(), DAY, START_TIME));
    }

    @Test
    void releaseForReservation_liberaSoloLaFilaDeEseOrigen() {
        adapter.registerForReservation(1L, court.getId(), DAY, START_TIME);

        adapter.releaseForReservation(1L);

        assertFalse(adapter.existsOccupancy(court.getId(), DAY, START_TIME));
    }

    @Test
    void releaseForReservation_conIdQueNoExiste_noRompeYNoAfectaOtrasFilas() {
        adapter.registerForReservation(1L, court.getId(), DAY, START_TIME);

        adapter.releaseForReservation(999_999L); // id ajeno — no debe borrar la fila real

        assertTrue(adapter.existsOccupancy(court.getId(), DAY, START_TIME));
    }

    @Test
    void registrarDosVecesElMismoSlot_violaElUniqueConstraint() {
        adapter.registerForReservation(1L, court.getId(), DAY, START_TIME);

        // Defensa en profundidad (sección 3.6/6.7 del diseño) — el UNIQUE de base rechaza el
        // segundo registro para el mismo (court, día, hora) aunque el caller no haya chequeado
        // existsOccupancy() antes.
        assertThrows(Exception.class, () -> adapter.registerForClassSession(2L, court.getId(), DAY, START_TIME));
    }
}
