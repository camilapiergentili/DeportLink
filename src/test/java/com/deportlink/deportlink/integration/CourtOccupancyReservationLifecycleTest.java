package com.deportlink.deportlink.integration;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.usecase.classsession.CreateClassSessionUseCase;
import com.deportlink.deportlink.application.usecase.reservation.*;
import com.deportlink.deportlink.enums.*;
import com.deportlink.deportlink.exception.SlotNotAvailableException;
import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CourtOccupancyReservationLifecycleTest extends OccupancyLifecycleFixture {
    @Autowired private BookReservationUseCase book;
    @Autowired private CancelReservationUseCase cancel;
    @Autowired private RescheduleReservationUseCase reschedule;

    @Test
    void cancelarReserva_liberaSuOcupacion() {
        var original = book.execute(courtId, playerId, day, originalTime);
        assertOccupancy("RESERVATION", original.id(), originalTime);
        cancel.execute(original.id(), playerId);
        assertStatus(original.id(), "CANCELADO");
        assertEmpty(originalTime);
        var replacement = book.execute(courtId, playerId, day, originalTime);
        assertOccupancy("RESERVATION", replacement.id(), originalTime);
        assertEquals(1L, count("SELECT COUNT(*) FROM court_occupancy WHERE court_id = ?", courtId));
    }

    @Test
    void reprogramarReserva_mueveLaOcupacion() {
        var original = book.execute(courtId, playerId, day, originalTime);
        var replacement = reschedule.execute(original.id(), playerId, day, destinationTime.plusHours(1));
        assertNotEquals(original.id(), replacement.id());
        assertStatus(original.id(), "REPROGRAMADO");
        assertActiveReservation(replacement.id(), destinationTime.plusHours(1));
        assertEmpty(originalTime);
        assertOccupancy("RESERVATION", replacement.id(), destinationTime.plusHours(1));
        assertEquals(2L, count("SELECT COUNT(*) FROM reservation WHERE court_id = ?", courtId));
        assertEquals(1L, count("SELECT COUNT(*) FROM court_occupancy WHERE court_id = ?", courtId));
    }

    @Test
    void destinoOcupadoPorClase_conservaLaReservaOriginal() {
        var original = book.execute(courtId, playerId, day, originalTime);
        var session = createSession.execute(admin, slotId, day);
        assertThrows(SlotNotAvailableException.class,
                () -> reschedule.execute(original.id(), playerId, day, destinationTime));
        assertActiveReservation(original.id(), originalTime);
        assertOccupancy("RESERVATION", original.id(), originalTime);
        assertOccupancy("CLASS_SESSION", session.id(), destinationTime);
        assertEquals(1L, count("SELECT COUNT(*) FROM reservation WHERE court_id = ?", courtId));
        assertEquals(2L, count("SELECT COUNT(*) FROM court_occupancy WHERE court_id = ?", courtId));
        assertEquals(1L, count("SELECT COUNT(*) FROM class_attendance WHERE class_session_id = ?", session.id()));
    }

    private void assertStatus(Long id, String status) {
        assertEquals(status, jdbc.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, id));
    }

    private void assertActiveReservation(Long id, LocalTime time) {
        assertEquals(1L, count("SELECT COUNT(*) FROM reservation WHERE id = ? AND court_id = ? AND player_id = ? AND reservation_day = ? AND start_time = ? AND status = 'RESERVADO'", id, courtId, playerId, day, time));
    }

    private void assertOccupancy(String source, Long id, LocalTime time) {
        assertEquals(1L, count("SELECT COUNT(*) FROM court_occupancy WHERE court_id = ? AND occupied_day = ? AND start_time = ? AND source_type = ? AND source_id = ?", courtId, day, time, source, id));
    }

    private void assertEmpty(LocalTime time) {
        assertEquals(0L, count("SELECT COUNT(*) FROM court_occupancy WHERE court_id = ? AND occupied_day = ? AND start_time = ?", courtId, day, time));
    }
}

/** Committed setup, isolated by court/player IDs. No test transaction hides service commits or rollback. */
abstract class OccupancyLifecycleFixture extends MySqlFlywayIntegrationTestBase {
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected CreateClassSessionUseCase createSession;
    @Autowired private OwnerRepository owners;
    @Autowired private ClubRepository clubs;
    @Autowired private BranchRepository branches;
    @Autowired private SportRepository sports;
    @Autowired private CourtRepository courts;
    @Autowired private PlayerRepository players;
    @Autowired private InstructorRepository instructors;
    @Autowired private ScheduleRepository schedules;
    @Autowired private ClassSlotRepository slots;
    @Autowired private ClassEnrollmentRepository enrollments;

    protected final Actor admin = new Actor(1L, ActorRole.ADMIN);
    protected final LocalTime originalTime = LocalTime.of(14, 0);
    protected final LocalTime destinationTime = LocalTime.of(15, 0);
    protected Long courtId, playerId, slotId;
    protected LocalDate day;

    @BeforeEach
    void prepareLifecycle() {
        day = LocalDate.now().plusWeeks(3);
        String suffix = UUID.randomUUID().toString();
        OwnerEntity owner = new OwnerEntity();
        owner.setEmail("owner-" + suffix + "@example.com");
        owner.setPassword("test"); owner.setFirstName("Test"); owner.setLastName("Owner");
        owner.setDni(20123456789L); owner.setCuil(suffix);
        owner.setDateOfBirth(LocalDate.of(1985, 1, 1));
        owner = owners.save(owner);
        ClubEntity club = new ClubEntity();
        club.setName("Lifecycle"); club.setLegalName(suffix); club.setCuit(suffix);
        club.getOwners().add(owner);
        club = clubs.save(club);
        BranchEntity branch = new BranchEntity();
        branch.setName("Lifecycle"); branch.setClub(club); branch.setCancellationWindowHours(12);
        AddressEntity address = new AddressEntity();
        address.setStreetName("Test"); address.setNumber(123); address.setCity("Buenos Aires");
        branch.setAddress(address);
        branch.setVerificationStatus(VerificationStatus.APPROVED);
        branch.setActiveStatus(ActiveStatus.ACTIVE);
        branch = branches.save(branch);
        SportEntity sport = new SportEntity(); sport.setNameSport("Padel"); sport = sports.save(sport);
        CourtEntity court = new CourtEntity();
        court.setName("Lifecycle"); court.setBranch(branch); court.setSport(sport);
        court.setPricePerHour(1000.0); court.setActiveStatus(ActiveStatus.ACTIVE);
        court = courts.save(court); courtId = court.getId();
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setCourt(court); schedule.setDay(day.getDayOfWeek());
        schedule.setOpeningTime(LocalTime.of(8, 0)); schedule.setClosingTime(LocalTime.of(22, 0));
        schedule.setSlotDuration(Duration.ofHours(1)); schedules.save(schedule);
        PlayerEntity player = new PlayerEntity();
        player.setEmail("player-" + suffix + "@example.com"); player.setPassword("test");
        player.setFirstName("Test"); player.setLastName("Player");
        player = players.save(player); playerId = player.getId();
        InstructorEntity instructor = new InstructorEntity();
        instructor.setEmail("instructor-" + suffix + "@example.com"); instructor.setPassword("test");
        instructor.setFirstName("Test"); instructor.setLastName("Instructor");
        instructor = instructors.save(instructor);
        ClassSlotEntity slot = new ClassSlotEntity();
        slot.setInstructor(instructor); slot.setCourt(court); slot.setDayOfWeek(day.getDayOfWeek());
        slot.setStartTime(destinationTime); slot.setDuration(Duration.ofHours(1));
        slot.setCapacity(4); slot.setLevel(Level.INTERMEDIO); slot.setActiveStatus(ActiveStatus.ACTIVE);
        slot = slots.save(slot); slotId = slot.getId();
        ClassEnrollmentEntity enrollment = new ClassEnrollmentEntity();
        enrollment.setClassSlot(slot); enrollment.setPlayer(player); enrollment.setActive(true);
        enrollments.save(enrollment);
    }

    protected long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}

