package com.deportlink.deportlink.integration;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.ClassSlotCourtGateway;
import com.deportlink.deportlink.application.usecase.classsession.CreateClassSessionUseCase;
import com.deportlink.deportlink.application.usecase.reservation.BookReservationUseCase;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.ClassSessionAlreadyExistsException;
import com.deportlink.deportlink.exception.CourtSlotOccupiedException;
import com.deportlink.deportlink.exception.SlotNotAvailableException;
import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * Nivel C — Reserva y clase compiten por cancha/fecha/hora, en ambas direcciones (ver
 * docs/class-management-stage-1c-persistence-design.md, sección 9.3).
 * <p>
 * Coordinación DETERMINÍSTICA, sin sleeps ni "ventaja de arranque": se espía (MockitoSpyBean,
 * delegando al bean real) el gateway que cada flujo usa para adquirir el lock pesimista sobre
 * Court como PRIMERA lectura de su transacción — {@link CourtGateway#findByIdForUpdate} para
 * BookReservationUseCase, {@link ClassSlotCourtGateway#findCourtIdByClassSlotForUpdate} para
 * CreateClassSessionUseCase. El hilo ganador cuenta un CountDownLatch recién DESPUÉS de que la
 * llamada real (que adquiere el lock de fila en MySQL) retorna; el hilo perdedor está bloqueado
 * en {@code latch.await()} y no arranca su propio intento hasta ese momento — garantiza, por
 * happens-before real, que el perdedor nunca empieza antes de que el ganador ya tenga el lock,
 * sin necesidad de que el ganador se pause a sí mismo ni de sondear estado alguno.
 */
class ReservationVsClassSessionConcurrencyTest extends MySqlFlywayIntegrationTestBase {

    @Autowired private BookReservationUseCase bookReservationUseCase;
    @Autowired private CreateClassSessionUseCase createClassSessionUseCase;
    @Autowired private com.deportlink.deportlink.application.usecase.classslot.ReactivateClassSlotUseCase reactivate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private ClassSlotRepository classSlotRepository;
    @Autowired private CourtOccupancyRepository courtOccupancyRepository;

    @MockitoSpyBean private CourtGateway courtGateway;
    @MockitoSpyBean private ClassSlotCourtGateway classSlotCourtGateway;

    private static final Actor ADMIN = new Actor(1L, ActorRole.ADMIN);

    private Long courtId;
    private Long playerId;
    private Long classSlotId;
    private LocalDate day;
    private LocalTime startTime;

    private static LocalDate nextThursday() {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.THURSDAY) d = d.plusDays(1);
        return d;
    }

    @BeforeEach
    void setUp() {
        day = nextThursday();
        startTime = LocalTime.of(15, 0);

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
        branch.setVerificationStatus(VerificationStatus.APPROVED);
        branch.setActiveStatus(ActiveStatus.ACTIVE);
        AddressEntity address = new AddressEntity();
        address.setStreetName("Test");
        address.setNumber(123);
        address.setCity("Buenos Aires");
        branch.setAddress(address);
        branch = branchRepository.save(branch);

        SportEntity sport = new SportEntity();
        sport.setNameSport("Pádel");
        sport = sportRepository.save(sport);

        CourtEntity court = new CourtEntity();
        court.setName("Cancha 1"); court.setBranch(branch); court.setSport(sport); court.setPricePerHour(1000.0);
        court.setActiveStatus(ActiveStatus.ACTIVE);
        court = courtRepository.save(court);
        courtId = court.getId();

        // Agenda de la cancha, para que BookReservationUseCase acepte el slot.
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setDay(day.getDayOfWeek());
        schedule.setOpeningTime(LocalTime.of(8, 0));
        schedule.setClosingTime(LocalTime.of(22, 0));
        schedule.setSlotDuration(Duration.ofHours(1));
        schedule.setCourt(court);
        scheduleRepository.save(schedule);

        PlayerEntity player = new PlayerEntity();
        player.setEmail("player-" + System.nanoTime() + "@example.com");
        player.setPassword("x"); player.setFirstName("P"); player.setLastName("P");
        player = playerRepository.save(player);
        playerId = player.getId();

        InstructorEntity instructor = new InstructorEntity();
        instructor.setEmail("instructor-" + System.nanoTime() + "@example.com");
        instructor.setPassword("x"); instructor.setFirstName("I"); instructor.setLastName("I");
        instructor = instructorRepository.save(instructor);

        ClassSlotEntity slot = new ClassSlotEntity();
        slot.setInstructor(instructor); slot.setCourt(court);
        slot.setDayOfWeek(day.getDayOfWeek()); slot.setStartTime(startTime);
        slot.setDuration(Duration.ofHours(1)); slot.setLevel(Level.INTERMEDIO);
        slot.setCapacity(4); slot.setActiveStatus(ActiveStatus.ACTIVE);
        slot = classSlotRepository.save(slot);
        classSlotId = slot.getId();
    }

    @Test
    void ganaLaReserva_laClaseFallaConElConflictoRealYNoQuedaOcupacionDuplicada() throws Exception {
        // An active weekly schedule already owns its starts, even before session generation.
        // Compete with reactivation of a paused schedule instead.
        jdbcTemplate.update("UPDATE class_slot SET active_status = 'INACTIVE' WHERE id = ?", classSlotId);
        CountDownLatch reservationHasLock = new CountDownLatch(1);
        doAnswer(inv -> {
            Object result = inv.callRealMethod(); // ejecuta el SELECT ... FOR UPDATE real
            reservationHasLock.countDown();
            return result;
        }).when(courtGateway).findByIdForUpdate(anyLong());

        AtomicReference<Throwable> reservationError = new AtomicReference<>();
        AtomicReference<Throwable> classError = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> reservationFuture = executor.submit(() -> {
            try {
                bookReservationUseCase.execute(courtId, playerId, day, startTime);
            } catch (Throwable t) {
                reservationError.set(t);
            }
        });
        Future<?> classFuture = executor.submit(() -> {
            try {
                reservationHasLock.await(30, TimeUnit.SECONDS); // gate determinístico, no un sleep arbitrario
                reactivate.execute(ADMIN, classSlotId);
            } catch (Throwable t) {
                classError.set(t);
            }
        });

        reservationFuture.get(30, TimeUnit.SECONDS);
        classFuture.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertNull(reservationError.get(), "La reserva debía ganar la carrera y no fallar");
        assertNotNull(classError.get(), "La clase debía perder la carrera");
        assertTrue(
                classError.get() instanceof CourtSlotOccupiedException
                        || isConstraintViolation(classError.get()),
                "Se esperaba CourtSlotOccupiedException o un conflicto de constraint de base — el punto real de rechazo depende del timing exacto; fue: " + classError.get()
        );

        assertEquals(1, countReservations(), "Debe existir exactamente una Reservation RESERVADO");
        assertEquals(0, countClassSessions(), "No debe haberse creado ninguna ClassSession");
        assertEquals(1, countOccupancyRows(), "Debe existir exactamente una fila de ocupación");
        assertEquals("RESERVATION", occupancySourceType(), "La ocupación debe pertenecer a la Reservation ganadora");
    }

    @Test
    void ganaLaClase_laReservaFallaConSlotNotAvailableYNoQuedaOcupacionDuplicada() throws Exception {
        jdbcTemplate.update("UPDATE class_slot SET active_status = 'INACTIVE' WHERE id = ?", classSlotId);
        CountDownLatch classHasLock = new CountDownLatch(1);
        doAnswer(inv -> {
            Object result = inv.callRealMethod();
            classHasLock.countDown();
            return result;
        }).when(classSlotCourtGateway).findCourtIdByClassSlotForUpdate(anyLong());

        AtomicReference<Throwable> reservationError = new AtomicReference<>();
        AtomicReference<Throwable> classError = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> classFuture = executor.submit(() -> {
            try {
                reactivate.execute(ADMIN, classSlotId);
            } catch (Throwable t) {
                classError.set(t);
            }
        });
        Future<?> reservationFuture = executor.submit(() -> {
            try {
                classHasLock.await(30, TimeUnit.SECONDS);
                bookReservationUseCase.execute(courtId, playerId, day, startTime);
            } catch (Throwable t) {
                reservationError.set(t);
            }
        });

        classFuture.get(30, TimeUnit.SECONDS);
        reservationFuture.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertNull(classError.get(), "La clase debía ganar la carrera y no fallar");
        assertNotNull(reservationError.get(), "La reserva debía perder la carrera");
        assertTrue(
                reservationError.get() instanceof SlotNotAvailableException
                        || isConstraintViolation(reservationError.get()),
                "Se esperaba SlotNotAvailableException o un conflicto de constraint de base; fue: " + reservationError.get()
        );

        assertEquals(0, countReservations(), "No debe existir ninguna Reservation RESERVADO");
        assertEquals(4, countClassSessions(), "Deben existir las cuatro próximas ClassSession");
        assertEquals(4, countOccupancyRows(), "Debe existir una ocupación por cada sesión");
        assertEquals("CLASS_SESSION", occupancySourceType(), "La ocupación debe pertenecer a la ClassSession ganadora");
    }

    @Test
    void dosCreacionesDeLaMismaSesion_soloUnaPersisteYLaOtraFallaConClassSessionAlreadyExists() throws Exception {
        // Ambas compiten por el MISMO lock de ClassSlot (mismo classSlotId) — quedan totalmente
        // serializadas entre sí (ver docs, sección 7.1/9.3): el perdedor SIEMPRE debe fallar con
        // la excepción de negocio (existsByClassSlotIdAndDay corre bajo ese mismo lock, antes de
        // cualquier INSERT), no con un conflicto de constraint crudo.
        CountDownLatch firstHasLock = new CountDownLatch(1);
        AtomicReference<Boolean> firstCall = new AtomicReference<>(true);
        doAnswer(inv -> {
            Object result = inv.callRealMethod();
            if (Boolean.TRUE.equals(firstCall.getAndSet(false))) {
                firstHasLock.countDown();
            }
            return result;
        }).when(classSlotCourtGateway).findCourtIdByClassSlotForUpdate(anyLong());

        AtomicReference<Throwable> firstError = new AtomicReference<>();
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> first = executor.submit(() -> {
            try {
                createClassSessionUseCase.execute(ADMIN, classSlotId, day);
            } catch (Throwable t) {
                firstError.set(t);
            }
        });
        Future<?> second = executor.submit(() -> {
            try {
                firstHasLock.await(30, TimeUnit.SECONDS);
                createClassSessionUseCase.execute(ADMIN, classSlotId, day);
            } catch (Throwable t) {
                secondError.set(t);
            }
        });

        first.get(30, TimeUnit.SECONDS);
        second.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = (firstError.get() == null ? 1 : 0) + (secondError.get() == null ? 1 : 0);
        assertEquals(1, successCount, "Exactamente una de las dos creaciones debe tener éxito");

        Throwable loserError = firstError.get() != null ? firstError.get() : secondError.get();
        assertInstanceOf(ClassSessionAlreadyExistsException.class, loserError,
                "El perdedor debe fallar específicamente por sesión duplicada, no por un conflicto genérico");

        assertEquals(1, countClassSessions(), "Debe existir exactamente una ClassSession para ese slot/fecha");
    }

    // ─── Helpers de verificación contra la base real ───────────────────────────────

    private boolean isConstraintViolation(Throwable t) {
        if (t == null) return false;
        String message = String.valueOf(t.getMessage()).toLowerCase();
        return t.getClass().getSimpleName().contains("DataIntegrityViolation")
                || message.contains("constraint") || message.contains("duplicate");
    }

    private int countReservations() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE court_id = ? AND status = 'RESERVADO'",
                Integer.class, courtId);
        return count == null ? 0 : count;
    }

    private int countClassSessions() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM class_session WHERE class_slot_id = ?", Integer.class, classSlotId);
        return count == null ? 0 : count;
    }

    private int countOccupancyRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM court_occupancy WHERE court_id = ?", Integer.class, courtId);
        return count == null ? 0 : count;
    }

    private String occupancySourceType() {
        List<String> types = jdbcTemplate.queryForList(
                "SELECT source_type FROM court_occupancy WHERE court_id = ?", String.class, courtId);
        return types.isEmpty() ? null : types.get(0);
    }
}

