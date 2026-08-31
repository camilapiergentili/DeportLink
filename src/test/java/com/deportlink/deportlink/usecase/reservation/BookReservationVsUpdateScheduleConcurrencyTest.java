package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.usecase.reservation.BookReservationUseCase;
import com.deportlink.deportlink.application.usecase.schedule.UpdateScheduleUseCase;
import com.deportlink.deportlink.exception.ReservationNotUpdateException;
import com.deportlink.deportlink.exception.SlotNotAvailableException;
import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de concurrencia contra MySQL REAL (Testcontainers) para la carrera entre "reservar" y
 * "achicar el horario de la agenda" (docs/software-review-2026-08-30.md, hallazgo F4).
 * <p>
 * Antes del fix, UpdateScheduleUseCase no tomaba ningún lock: su chequeo "¿las reservas activas
 * de este día entran en el nuevo rango horario?" corría sin sincronizarse con el
 * SELECT ... FOR UPDATE que BookReservationUseCase toma sobre la cancha. Era posible que
 * UpdateScheduleUseCase aprobara un rango más angosto en el mismo instante en que
 * BookReservationUseCase confirmaba una reserva fuera de ese rango — dejando una reserva
 * RESERVADO fuera del horario comercial recién guardado, sin que nada lo detectara.
 * <p>
 * El fix hace que ambos casos de uso tomen el lock pesimista sobre la misma fila de Court como
 * primera operación de su transacción (mismo mecanismo que BookReservationVsDeleteCourtConcurrencyTest),
 * así que quedan serializados entre sí sin importar cuál arranca primero.
 * <p>
 * Mismo setup de Testcontainers que BookReservationConcurrencyTest — ver esa clase para el
 * razonamiento de por qué no se usa el perfil "test" (H2) ni Flyway acá.
 */
@Testcontainers
@SpringBootTest
class BookReservationVsUpdateScheduleConcurrencyTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.23")
            .withDatabaseName("deportlink_concurrency_update_schedule")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LWxvbmctZW5vdWdoLWZvci1qd3Qtc2lnbmluZw==");
        registry.add("jwt.expiration", () -> "36000000");
        registry.add("cors.allowed-origins", () -> "http://localhost:5173");
    }

    @Autowired private BookReservationUseCase bookReservationUseCase;
    @Autowired private UpdateScheduleUseCase updateScheduleUseCase;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private ReservationRepository reservationRepository;

    private Long courtId;
    private Long scheduleId;
    private Long playerId;
    private LocalDate day;

    // La agenda original cubre 08:00-22:00. Se reserva el último slot (21:00-22:00) y, en
    // paralelo, se intenta achicar el horario a 08:00-20:00 — un rango que ya no lo contiene.
    private static final LocalTime BOOKED_SLOT = LocalTime.of(21, 0);
    private static final String NEW_OPENING = "08:00";
    private static final String NEW_CLOSING = "20:00";

    @BeforeEach
    void setUp() {
        var fixtures = new ConcurrencyTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, scheduleRepository, playerRepository);

        day = LocalDate.now().plusDays(7);

        var branch = fixtures.createBranchWithCourts("update-schedule-race", day, 1);
        courtId = branch.courtId();
        scheduleId = scheduleRepository.findByCourtId(courtId).get(0).getId();

        playerId = fixtures.createPlayers("update-schedule-race", 1).get(0);
    }

    @RepeatedTest(5)
    void execute_reservarYAchicarElHorarioEnParalelo_resultadoSiempreConsistente() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            CompletableFuture<Result> bookFuture = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                awaitUninterruptibly(start);
                try {
                    bookReservationUseCase.execute(courtId, playerId, day, BOOKED_SLOT);
                    return Result.success();
                } catch (Exception ex) {
                    return Result.failure(ex);
                }
            }, executor);

            CompletableFuture<Result> updateFuture = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                awaitUninterruptibly(start);
                try {
                    updateScheduleUseCase.execute(scheduleId, courtId, NEW_OPENING, NEW_CLOSING);
                    return Result.success();
                } catch (Exception ex) {
                    return Result.failure(ex);
                }
            }, executor);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Result bookResult = bookFuture.join();
            Result updateResult = updateFuture.join();

            boolean bookWon = bookResult.ok();
            boolean updateWon = updateResult.ok();

            // Nunca los dos a la vez, y nunca ninguno de los dos: exactamente uno de los dos
            // debe haber visto el estado que dejó el otro ya confirmado.
            assertThat(bookWon ^ updateWon)
                    .as("Exactamente uno de los dos debe ganar la carrera — reservar o achicar el horario,"
                            + " nunca ambos ni ninguno. book=%s (%s), update=%s (%s)",
                            bookWon, bookResult.error(), updateWon, updateResult.error())
                    .isTrue();

            if (bookWon) {
                // La reserva de las 21:00 ganó primero: el update, al leer las reservas activas
                // del día DESPUÉS de que el lock se liberó, debe haber visto esa reserva y
                // haberse rechazado porque ya no entra en el rango propuesto (08:00-20:00).
                assertThat(updateResult.error()).isInstanceOf(ReservationNotUpdateException.class);

                ScheduleEntity schedule = scheduleRepository.findById(scheduleId).orElseThrow();
                assertThat(schedule.getClosingTime())
                        .as("El horario NO debe haberse achicado — el update fue rechazado")
                        .isEqualTo(LocalTime.of(22, 0));

                List<ReservationEntity> reservations = reservationRepository.findAll().stream()
                        .filter(r -> r.getCourt().getId().equals(courtId))
                        .toList();
                assertThat(reservations)
                        .as("Debe existir la reserva de las 21:00 que ganó la carrera, y ningún horario "
                                + "vigente la deja afuera")
                        .hasSize(1);
            } else {
                // El update ganó primero: la reserva, al leer la agenda DESPUÉS de que el lock
                // se liberó, debe ver el horario ya achicado (08:00-20:00) y rechazar las 21:00
                // por no ser un slot válido — nunca debe quedar una reserva fuera de horario.
                assertThat(bookResult.error()).isInstanceOf(SlotNotAvailableException.class);

                ScheduleEntity schedule = scheduleRepository.findById(scheduleId).orElseThrow();
                assertThat(schedule.getClosingTime())
                        .as("El horario debe haberse achicado — el update ganó la carrera")
                        .isEqualTo(LocalTime.of(20, 0));

                List<ReservationEntity> reservations = reservationRepository.findAll().stream()
                        .filter(r -> r.getCourt().getId().equals(courtId))
                        .toList();
                assertThat(reservations)
                        .as("No debe haberse confirmado ninguna reserva fuera del horario vigente")
                        .isEmpty();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private record Result(boolean ok, Throwable error) {
        static Result success() {
            return new Result(true, null);
        }

        static Result failure(Throwable error) {
            return new Result(false, error);
        }
    }
}
