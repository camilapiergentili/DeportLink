package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.usecase.court.DeleteCourtUseCase;
import com.deportlink.deportlink.application.usecase.reservation.BookReservationUseCase;
import com.deportlink.deportlink.exception.CourtHasReservationsException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
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

import java.time.Duration;
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
 * "borrar la cancha" — la que motivó agregar el lock pesimista a DeleteCourtUseCase.
 * <p>
 * Sin ese lock, DeleteCourtUseCase.hasReservations() es una lectura simple que, bajo
 * REPEATABLE READ, no se sincroniza con el SELECT ... FOR UPDATE de BookReservationUseCase —
 * es posible verificar "no tiene reservas" en el mismo instante en que la otra transacción
 * confirma una reserva nueva, y el DELETE choca después contra la FK (o peor, si la FK no
 * frenara nada, dejaría una reserva huérfana apuntando a una cancha borrada).
 * <p>
 * Mismo setup de Testcontainers que BookReservationConcurrencyTest — ver esa clase para el
 * razonamiento de por qué no se usa el perfil "test" (H2) ni Flyway acá.
 */
@Testcontainers
@SpringBootTest
class BookReservationVsDeleteCourtConcurrencyTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.23")
            .withDatabaseName("deportlink_concurrency_delete")
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
    @Autowired private DeleteCourtUseCase deleteCourtUseCase;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private ReservationRepository reservationRepository;

    private Long courtId;
    private Long playerId;
    private LocalDate day;
    private LocalTime startTime;

    @BeforeEach
    void setUp() {
        OwnerEntity owner = new OwnerEntity();
        owner.setEmail("owner-delete-race-" + System.nanoTime() + "@example.com");
        owner.setPassword("irrelevant");
        owner.setFirstName("Test");
        owner.setLastName("Owner");
        owner.setDni(20123456789L);
        owner.setCuil("cuil-" + System.nanoTime());
        owner.setDateOfBirth(LocalDate.of(1985, 1, 1));
        owner = ownerRepository.save(owner);

        SportEntity sport = new SportEntity();
        sport.setNameSport("Football");
        sport = sportRepository.save(sport);

        ClubEntity club = new ClubEntity();
        club.setName("Club Delete Race");
        club.setLegalName("Club Delete Race " + System.nanoTime());
        club.setCuit("cuit-" + System.nanoTime());
        club.getOwners().add(owner);
        club = clubRepository.save(club);

        AddressEntity address = new AddressEntity();
        address.setStreetName("Av. Siempre Viva");
        address.setNumber(742);
        address.setCity("CABA");
        address.setProvince("Buenos Aires");
        address.setPostalCode(1043);
        address.setLatitude(-34.6);
        address.setLongitude(-58.4);

        BranchEntity branch = new BranchEntity();
        branch.setClub(club);
        branch.setName("Sucursal Delete Race");
        branch.setCancellationWindowHours(12);
        branch.setAddress(address);
        branch = branchRepository.save(branch);

        CourtEntity court = new CourtEntity();
        court.setName("Cancha Delete Race");
        court.setBranch(branch);
        court.setSport(sport);
        court.setPricePerHour(100.0);
        court = courtRepository.save(court);
        courtId = court.getId();

        day = LocalDate.now().plusDays(7);
        startTime = LocalTime.of(10, 0);

        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setDay(day.getDayOfWeek());
        schedule.setOpeningTime(LocalTime.of(8, 0));
        schedule.setClosingTime(LocalTime.of(22, 0));
        schedule.setSlotDuration(Duration.ofHours(1));
        schedule.setCourt(court);
        scheduleRepository.save(schedule);

        PlayerEntity player = new PlayerEntity();
        player.setEmail("player-delete-race-" + System.nanoTime() + "@example.com");
        player.setPassword("irrelevant");
        player.setFirstName("Player");
        player.setLastName("One");
        playerId = playerRepository.save(player).getId();
    }

    @RepeatedTest(5)
    void execute_reservarYBorrarLaMismaCanchaEnParalelo_resultadoSiempreConsistente() throws InterruptedException {
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
                    deleteCourtUseCase.execute(courtId);
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
                    .as("Exactamente uno de los dos debe ganar la carrera — reservar o borrar, nunca ambos ni ninguno."
                            + " book=%s (%s), delete=%s (%s)",
                            bookWon, bookResult.error(), deleteWon, deleteResult.error())
                    .isTrue();

            if (bookWon) {
                // La reserva ganó primero: el delete debe haber visto la reserva recién
                // confirmada (gracias al lock) y haberse rechazado por eso — no por una FK
                // violation ni por ningún otro error.
                assertThat(deleteResult.error()).isInstanceOf(CourtHasReservationsException.class);

                assertThat(courtRepository.findById(courtId))
                        .as("La cancha debe seguir existiendo — el delete no debió pasar")
                        .isPresent();

                List<ReservationEntity> reservations = reservationRepository.findAll().stream()
                        .filter(r -> r.getCourt().getId().equals(courtId))
                        .toList();
                assertThat(reservations)
                        .as("Debe existir exactamente la reserva que ganó la carrera")
                        .hasSize(1);
            } else {
                // El delete ganó primero: la reserva, al tomar su propio lock sobre la cancha
                // después de que ya fue borrada y comiteada, debe encontrarla inexistente.
                assertThat(bookResult.error()).isInstanceOf(CourtNotFoundException.class);

                assertThat(courtRepository.findById(courtId))
                        .as("La cancha ya no debe existir — el delete ganó la carrera")
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
