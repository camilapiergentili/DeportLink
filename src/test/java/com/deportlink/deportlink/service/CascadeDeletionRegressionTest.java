package com.deportlink.deportlink.service;

import com.deportlink.deportlink.application.usecase.branch.DeleteBranchUseCase;
import com.deportlink.deportlink.application.usecase.court.DeleteCourtUseCase;
import com.deportlink.deportlink.application.usecase.player.DeletePlayerUseCase;
import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.exception.BranchHasReservationsException;
import com.deportlink.deportlink.exception.CourtHasReservationsException;
import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de regresión para el problema P0.1 (borrado en cascada de Reservation/Ticket
 * al eliminar Player, Court o Branch). No cubren otro comportamiento del sistema —
 * para eso están los demás test classes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CascadeDeletionRegressionTest {

    @Autowired private DeletePlayerUseCase deletePlayerUseCase;
    @Autowired private DeleteCourtUseCase deleteCourtUseCase;
    @Autowired private DeleteBranchUseCase deleteBranchUseCase;

    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private EntityManager entityManager;

    private PlayerEntity testPlayer;
    private BranchEntity testBranch;
    private CourtEntity testCourt;

    @BeforeEach
    void setUp() {
        testPlayer = new PlayerEntity();
        testPlayer.setEmail("player-cascade@example.com");
        testPlayer.setPassword("password123");
        testPlayer.setFirstName("Test");
        testPlayer.setLastName("Player");
        testPlayer.setPhone("1234567890");
        testPlayer = playerRepository.save(testPlayer);

        SportEntity sport = new SportEntity();
        sport.setNameSport("Football");
        sport = sportRepository.save(sport);

        OwnerEntity owner = new OwnerEntity();
        owner.setEmail("owner-cascade@example.com");
        owner.setPassword("password123");
        owner.setFirstName("Test");
        owner.setLastName("Owner");
        owner.setPhone("1234567890");
        owner.setDni(20123456789L);
        owner.setCuil("20123456789");
        owner.setDateOfBirth(LocalDate.of(1985, 1, 1));
        owner = ownerRepository.save(owner);

        ClubEntity club = new ClubEntity();
        club.setName("Test Club Cascade");
        club.setLegalName("Test Club Cascade Legal");
        club.setCuit("30199999999");
        club.getOwners().add(owner);
        club = clubRepository.save(club);

        testBranch = new BranchEntity();
        testBranch.setClub(club);
        testBranch.setName("Test Branch Cascade");
        testBranch.setCancellationWindowHours(12);
        testBranch = branchRepository.save(testBranch);

        testCourt = new CourtEntity();
        testCourt.setName("Test Court Cascade");
        testCourt.setBranch(testBranch);
        testCourt.setSport(sport);
        testCourt.setPricePerHour(100.0);
        testCourt = courtRepository.save(testCourt);
    }

    private ReservationEntity createReservationWithTicket() {
        ReservationEntity reservation = new ReservationEntity();
        reservation.setPlayer(testPlayer);
        reservation.setCourt(testCourt);
        reservation.setDay(LocalDate.now().plusDays(5));
        reservation.setStartTime(LocalTime.of(20, 0));
        reservation.setDuration(Duration.ofMinutes(60));
        reservation.setStatus(StatusReservation.RESERVADO);

        TicketEntity ticket = new TicketEntity();
        ticket.setReservation(reservation);
        ticket.setPlayerName(testPlayer.getFirstName());
        ticket.setPlayerLastName(testPlayer.getLastName());
        ticket.setCourtName(testCourt.getName());
        ticket.setSport("Football");
        ticket.setBranchName(testBranch.getName());
        ticket.setBranchAddress("Av. Siempre Viva 123");
        ticket.setTotalPrice(100.0);
        ticket.setIssuedAt(LocalDateTime.now());
        reservation.setTicket(ticket);

        return reservationRepository.save(reservation);
    }

    /**
     * Fuerza el flush de cambios pendientes y desconecta el persistence context.
     * Necesario porque en el test, setUp() y el @Test comparten la misma sesión de
     * Hibernate (identity map) — sin esto, las colecciones bidireccionales que solo
     * se linkearon desde el lado @ManyToOne (ej. testCourt.setBranch(testBranch),
     * nunca testBranch.getCourts().add(testCourt)) siguen vacías en memoria y el
     * cascade no tiene nada que recorrer. En producción cada request usa un
     * persistence context nuevo, así que esto no ocurre — este flush+clear() replica
     * esa condición real antes de cada operación de borrado.
     */
    private void reloadPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    // ─── 1 y 2: Player ──────────────────────────────────────────────────────────

    @Test
    void deletePlayer_noEliminaSusReservations() {
        ReservationEntity reservation = createReservationWithTicket();
        Long reservationId = reservation.getId();
        reloadPersistenceContext();

        deletePlayerUseCase.execute(testPlayer.getId());

        assertTrue(reservationRepository.findById(reservationId).isPresent(),
                "La reserva debe seguir existiendo después de anonimizar al jugador");
    }

    @Test
    void deletePlayer_noEliminaSusTickets() {
        ReservationEntity reservation = createReservationWithTicket();
        Long reservationId = reservation.getId();
        Long ticketId = reservation.getTicket().getId();
        reloadPersistenceContext();

        deletePlayerUseCase.execute(testPlayer.getId());

        ReservationEntity afterwards = reservationRepository.findById(reservationId).orElseThrow();
        assertNotNull(afterwards.getTicket(), "El ticket debe seguir existiendo después de anonimizar al jugador");
        assertEquals(ticketId, afterwards.getTicket().getId());
    }

    // ─── 7: anonimización del Player ────────────────────────────────────────────

    @Test
    void deletePlayer_anonimizaCamposYElEmailViejoDejaDeResolver() {
        createReservationWithTicket();
        String originalEmail = testPlayer.getEmail();
        Long playerId = testPlayer.getId();
        reloadPersistenceContext();

        deletePlayerUseCase.execute(playerId);

        PlayerEntity afterwards = playerRepository.findById(playerId).orElseThrow();
        assertNotEquals(originalEmail, afterwards.getEmail(), "El email debe cambiar a uno anonimizado");
        assertTrue(playerRepository.findByEmail(originalEmail).isEmpty(),
                "El email original ya no debe resolver a ningún usuario (no se puede loguear con las credenciales viejas)");
        assertNull(afterwards.getPhone(), "El teléfono debe limpiarse");
        assertEquals(1, reservationRepository.findByPlayer_Id(playerId).size(),
                "La reserva del jugador anonimizado debe seguir asociada a su id");
    }

    // ─── 3 y 5: Court ───────────────────────────────────────────────────────────

    @Test
    void deleteCourt_conReservas_falla() {
        createReservationWithTicket();
        reloadPersistenceContext();

        assertThrows(CourtHasReservationsException.class,
                () -> deleteCourtUseCase.execute(testCourt.getId()));
        assertTrue(courtRepository.findById(testCourt.getId()).isPresent(),
                "La cancha no debe eliminarse si tiene reservas");
    }

    @Test
    void deleteCourt_sinReservas_funciona() {
        reloadPersistenceContext();
        deleteCourtUseCase.execute(testCourt.getId());

        assertTrue(courtRepository.findById(testCourt.getId()).isEmpty());
    }

    // ─── 4 y 6: Branch ──────────────────────────────────────────────────────────

    @Test
    void deleteBranch_conReservasEnAlgunaCourt_falla() {
        createReservationWithTicket();
        reloadPersistenceContext();

        assertThrows(BranchHasReservationsException.class,
                () -> deleteBranchUseCase.execute(testBranch.getId()));
        assertTrue(branchRepository.findById(testBranch.getId()).isPresent(),
                "La sucursal no debe eliminarse si alguna de sus canchas tiene reservas");
    }

    @Test
    void deleteBranch_sinReservas_funciona() {
        reloadPersistenceContext();
        deleteBranchUseCase.execute(testBranch.getId());

        assertTrue(branchRepository.findById(testBranch.getId()).isEmpty());
        assertTrue(courtRepository.findById(testCourt.getId()).isEmpty(),
                "La cancha vacía debe eliminarse junto con la sucursal (cascade Branch→Court intacto)");
    }
}
