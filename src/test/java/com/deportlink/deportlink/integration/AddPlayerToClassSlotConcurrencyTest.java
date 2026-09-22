package com.deportlink.deportlink.integration;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.usecase.classslot.AddPlayerToClassSlotUseCase;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.ClassSlotFullException;
import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * Nivel C — dos altas de alumno compiten por el último lugar disponible (ver
 * docs/class-management-stage-1c-persistence-design.md, sección 9.3). Mismo mecanismo de
 * coordinación determinística que ReservationVsClassSessionConcurrencyTest: se espía
 * ClassSlotRepositoryPort.findByIdForUpdate (el lock que AddPlayerToClassSlotUseCase toma como
 * primera lectura) y se cuenta el latch recién cuando la llamada real ya adquirió el lock.
 */
class AddPlayerToClassSlotConcurrencyTest extends MySqlFlywayIntegrationTestBase {

    @Autowired private AddPlayerToClassSlotUseCase addPlayerToClassSlotUseCase;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private ClassSlotRepository classSlotRepository;
    @Autowired private ClassEnrollmentRepository classEnrollmentRepository;

    @MockitoSpyBean private ClassSlotRepositoryPort classSlotRepositoryPort;

    private static final Actor ADMIN = new Actor(1L, ActorRole.ADMIN);

    private Long classSlotId;
    private Long playerAId;
    private Long playerBId;

    @BeforeEach
    void setUp() {
        OwnerEntity owner = new OwnerEntity();
        owner.setEmail("owner-" + System.nanoTime() + "@example.com");
        owner.setPassword("x"); owner.setFirstName("O"); owner.setLastName("O");
        owner.setDni(20123456789L); owner.setCuil("cuil-" + System.nanoTime());
        owner.setDateOfBirth(LocalDate.of(1985, 1, 1));
        owner = ownerRepository.save(owner);

        ClubEntity club = new ClubEntity();
        club.setName("Club"); club.setLegalName("Club Legal " + System.nanoTime());
        club.setCuit("cuit-" + System.nanoTime());
        club.getOwners().add(owner);
        club = clubRepository.save(club);

        BranchEntity branch = new BranchEntity();
        branch.setClub(club); branch.setName("Sucursal"); branch.setCancellationWindowHours(12);
        branch = branchRepository.save(branch);

        SportEntity sport = new SportEntity();
        sport.setNameSport("Pádel");
        sport = sportRepository.save(sport);

        CourtEntity court = new CourtEntity();
        court.setName("Cancha 1"); court.setBranch(branch); court.setSport(sport); court.setPricePerHour(1000.0);
        court = courtRepository.save(court);

        InstructorEntity instructor = new InstructorEntity();
        instructor.setEmail("instructor-" + System.nanoTime() + "@example.com");
        instructor.setPassword("x"); instructor.setFirstName("I"); instructor.setLastName("I");
        instructor = instructorRepository.save(instructor);

        // Capacidad 1, con 0 activos — un único lugar en juego.
        ClassSlotEntity slot = new ClassSlotEntity();
        slot.setInstructor(instructor); slot.setCourt(court);
        slot.setDayOfWeek(DayOfWeek.THURSDAY); slot.setStartTime(LocalTime.of(15, 0));
        slot.setDuration(Duration.ofHours(1)); slot.setLevel(Level.INTERMEDIO);
        slot.setCapacity(1); slot.setActiveStatus(ActiveStatus.ACTIVE);
        slot = classSlotRepository.save(slot);
        classSlotId = slot.getId();

        PlayerEntity playerA = new PlayerEntity();
        playerA.setEmail("player-a-" + System.nanoTime() + "@example.com");
        playerA.setPassword("x"); playerA.setFirstName("A"); playerA.setLastName("A");
        playerAId = playerRepository.save(playerA).getId();

        PlayerEntity playerB = new PlayerEntity();
        playerB.setEmail("player-b-" + System.nanoTime() + "@example.com");
        playerB.setPassword("x"); playerB.setFirstName("B"); playerB.setLastName("B");
        playerBId = playerRepository.save(playerB).getId();
    }

    @Test
    void dosAltasCompitenPorElUltimoLugar_soloUnaEntraYLaOtraFallaConClassSlotFull() throws Exception {
        CountDownLatch firstHasLock = new CountDownLatch(1);
        AtomicReference<Boolean> firstCall = new AtomicReference<>(true);
        doAnswer(inv -> {
            Object result = inv.callRealMethod();
            if (Boolean.TRUE.equals(firstCall.getAndSet(false))) {
                firstHasLock.countDown();
            }
            return result;
        }).when(classSlotRepositoryPort).findByIdForUpdate(anyLong());

        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> futureA = executor.submit(() -> {
            try {
                addPlayerToClassSlotUseCase.execute(ADMIN, classSlotId, playerAId);
            } catch (Throwable t) {
                errorA.set(t);
            }
        });
        Future<?> futureB = executor.submit(() -> {
            try {
                firstHasLock.await(30, TimeUnit.SECONDS);
                addPlayerToClassSlotUseCase.execute(ADMIN, classSlotId, playerBId);
            } catch (Throwable t) {
                errorB.set(t);
            }
        });

        futureA.get(30, TimeUnit.SECONDS);
        futureB.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = (errorA.get() == null ? 1 : 0) + (errorB.get() == null ? 1 : 0);
        assertEquals(1, successCount, "Exactamente una alta debe tener éxito");

        Throwable loserError = errorA.get() != null ? errorA.get() : errorB.get();
        assertInstanceOf(ClassSlotFullException.class, loserError,
                "El perdedor debe fallar específicamente por capacidad, gracias al lock serializando ambos intentos");

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM class_enrollment WHERE class_slot_id = ? AND active = TRUE",
                Integer.class, classSlotId);
        assertEquals(1, activeCount, "Debe existir exactamente un ClassEnrollment activo — la capacidad nunca se superó");
    }
}
