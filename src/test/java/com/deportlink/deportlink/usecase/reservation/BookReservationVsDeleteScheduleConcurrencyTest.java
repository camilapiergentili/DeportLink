package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.usecase.reservation.BookReservationUseCase;
import com.deportlink.deportlink.application.usecase.schedule.DeleteScheduleUseCase;
import com.deportlink.deportlink.exception.ScheduleHasReservationsException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
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
 * "borrar la franja de agenda de ese día" (docs/software-review-2026-08-30.md, hallazgo F4).
 * <p>
 * Antes del fix, DeleteScheduleUseCase.existsReservationForDay() era una lectura simple que no
 * se sincronizaba con el SELECT ... FOR UPDATE que BookReservationUseCase toma sobre la cancha
 * — a diferencia de UpdateScheduleUseCase, acá nunca existió una query hermana con @Lock para
 * este chequeo puntual. Era posible borrar la franja horaria en el mismo instante en que se
 * confirmaba una reserva para ese día, dejando una reserva RESERVADO sin ninguna agenda vigente
 * que la respalde.
 * <p>
 * El fix hace que DeleteScheduleUseCase tome el mismo lock pesimista sobre Court que ya toma
 * BookReservationUseCase (y ahora también UpdateScheduleUseCase) como primera operación de su
 * transacción — sin necesidad de escribir ninguna query nueva, porque el lock por sí solo ya
 * serializa a ambas transacciones entre sí.
 */
@Testcontainers
@SpringBootTest
class BookReservationVsDeleteScheduleConcurrencyTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.23")
            .withDatabaseName("deportlink_concurrency_delete_schedule")
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
    @Autowired private DeleteScheduleUseCase deleteScheduleUseCase;
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
    private LocalTime startTime;

    @BeforeEach
    void setUp() {
        var fixtures = new ConcurrencyTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, scheduleRepository, playerRepository);

        day = LocalDate.now().plusDays(7);
        startTime = LocalTime.of(10, 0);

        var branch = fixtures.createBranchWithCourts("delete-schedule-race", day, 1);
        courtId = branch.courtId();
        scheduleId = scheduleRepository.findByCourtId(courtId).get(0).getId();

        playerId = fixtures.createPlayers("delete-schedule-race", 1).get(0);
    }

    @RepeatedTest(5)
    void execute_reservarYBorrarLaAgendaDeEseDiaEnParalelo_resultadoSiempreConsistente() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            CompletableFuture<Result> bookFuture = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                awaitUninterruptibly(start);
                try {
                    bookReservationUseCase.execute(courtId, playerId, day, startTime);
                    return Result.success();
                } catch (Exception ex) {
                    return Result.failure(ex);
                }
            }, executor);

            CompletableFuture<Result> deleteFuture = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                awaitUninterruptibly(start);
                try {
                    deleteScheduleUseCase.execute(scheduleId, courtId);
                    return Result.success();
                } catch (Exception ex) {
                    return Result.failure(ex);
                }
            }, executor);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Result bookResult = bookFuture.join();
            Result deleteResult = deleteFuture.join();

            boolean bookWon = bookResult.ok();
            boolean deleteWon = deleteResult.ok();

            // Nunca los dos a la vez, y nunca ninguno de los dos.
            assertThat(bookWon ^ deleteWon)
                    .as("Exactamente uno de los dos debe ganar la carrera — reservar o borrar la agenda,"
                            + " nunca ambos ni ninguno. book=%s (%s), delete=%s (%s)",
                            bookWon, bookResult.error(), deleteWon, deleteResult.error())
                    .isTrue();

            if (bookWon) {
                // La reserva ganó primero: el delete, al chequear reservas DESPUÉS de que el
                // lock se liberó, debe haber visto la reserva recién confirmada y rechazarse.
                assertThat(deleteResult.error()).isInstanceOf(ScheduleHasReservationsException.class);

                assertThat(scheduleRepository.findById(scheduleId))
                        .as("La franja de agenda debe seguir existiendo — el delete no debió pasar")
                        .isPresent();

                List<ReservationEntity> reservations = reservationRepository.findAll().stream()
                        .filter(r -> r.getCourt().getId().equals(courtId))
                        .toList();
                assertThat(reservations)
                        .as("Debe existir exactamente la reserva que ganó la carrera, con su agenda vigente")
                        .hasSize(1);
            } else {
                // El delete ganó primero: la reserva, al leer la agenda DESPUÉS de que el lock
                // se liberó, debe encontrarla ya borrada — nunca debe quedar una reserva activa
                // sin ninguna franja horaria que la respalde.
                assertThat(bookResult.error()).isInstanceOf(ScheduleNotFoundException.class);

                assertThat(scheduleRepository.findById(scheduleId))
                        .as("La franja de agenda ya no debe existir — el delete ganó la carrera")
                        .isEmpty();

                List<ReservationEntity> reservations = reservationRepository.findAll().stream()
                        .filter(r -> r.getCourt().getId().equals(courtId))
                        .toList();
                assertThat(reservations)
                        .as("No debe haberse confirmado ninguna reserva sin agenda que la respalde")
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
