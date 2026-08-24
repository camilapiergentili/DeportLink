package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.usecase.branch.DeleteBranchUseCase;
import com.deportlink.deportlink.application.usecase.reservation.BookReservationUseCase;
import com.deportlink.deportlink.exception.BranchHasReservationsException;
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
 * Equivalente a {@link BookReservationVsDeleteCourtConcurrencyTest} pero para
 * {@link DeleteBranchUseCase} — ejercita específicamente el caso que ese test no cubre:
 * {@code findByBranchIdForUpdate} bloquea una LISTA de canchas (acá, dos) en una sola
 * sentencia, no una fila única como {@code findByIdForUpdate}.
 * <p>
 * La sucursal tiene dos canchas: courtA (la que se reserva) y courtB (sin reservas, solo para
 * probar que el lock múltiple no genere un borrado parcial cuando el delete falla).
 * <p>
 * Mismo setup de Testcontainers que los demás tests de esta familia.
 */
@Testcontainers
@SpringBootTest
class BookReservationVsDeleteBranchConcurrencyTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.23")
            .withDatabaseName("deportlink_concurrency_delete_branch")
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
    @Autowired private DeleteBranchUseCase deleteBranchUseCase;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private ReservationRepository reservationRepository;

    private Long branchId;
    private Long courtAId;
    private Long courtBId;
    private Long playerId;
    private LocalDate day;
    private LocalTime startTime;

    @BeforeEach
    void setUp() {
        OwnerEntity owner = new OwnerEntity();
        owner.setEmail("owner-delete-branch-race-" + System.nanoTime() + "@example.com");
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
        club.setName("Club Delete Branch Race");
        club.setLegalName("Club Delete Branch Race " + System.nanoTime());
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
        branch.setName("Sucursal Delete Branch Race");
        branch.setAddress(address);
        branch = branchRepository.save(branch);
        branchId = branch.getId();

        // Dos canchas en la misma sucursal — la clave de este test: findByBranchIdForUpdate
        // tiene que bloquear AMBAS filas en una sola sentencia, no solo la que se reserva.
        CourtEntity courtA = new CourtEntity();
        courtA.setName("Cancha A");
        courtA.setBranch(branch);
        courtA.setSport(sport);
        courtA.setPricePerHour(100.0);
        courtA = courtRepository.save(courtA);
        courtAId = courtA.getId();

        CourtEntity courtB = new CourtEntity();
        courtB.setName("Cancha B");
        courtB.setBranch(branch);
        courtB.setSport(sport);
        courtB.setPricePerHour(100.0);
        courtB = courtRepository.save(courtB);
        courtBId = courtB.getId();

        day = LocalDate.now().plusDays(7);
        startTime = LocalTime.of(10, 0);

        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setDay(day.getDayOfWeek());
        schedule.setOpeningTime(LocalTime.of(8, 0));
        schedule.setClosingTime(LocalTime.of(22, 0));
        schedule.setSlotDuration(Duration.ofHours(1));
        schedule.setCourt(courtA);
        scheduleRepository.save(schedule);

        PlayerEntity player = new PlayerEntity();
        player.setEmail("player-delete-branch-race-" + System.nanoTime() + "@example.com");
        player.setPassword("irrelevant");
        player.setFirstName("Player");
        player.setLastName("One");
        playerId = playerRepository.save(player).getId();
    }

    @RepeatedTest(5)
    void execute_reservarUnaCanchaYBorrarLaSucursalEnParalelo_resultadoSiempreConsistenteYSinEstadoParcial()
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            CompletableFuture<Result> bookFuture = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                awaitUninterruptibly(start);
                try {
                    bookReservationUseCase.execute(courtAId, playerId, day, startTime);
                    return Result.success();
                } catch (Exception ex) {
                    return Result.failure(ex);
                }
            }, executor);

            CompletableFuture<Result> deleteFuture = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                awaitUninterruptibly(start);
                try {
                    deleteBranchUseCase.execute(branchId);
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

            assertThat(bookWon ^ deleteWon)
                    .as("Exactamente uno de los dos debe ganar la carrera — reservar o borrar la"
                            + " sucursal, nunca ambos ni ninguno. book=%s (%s), delete=%s (%s)",
                            bookWon, bookResult.error(), deleteWon, deleteResult.error())
                    .isTrue();

            if (bookWon) {
                assertThat(deleteResult.error()).isInstanceOf(BranchHasReservationsException.class);

                // ─── Sin estado parcial: la sucursal Y AMBAS canchas siguen existiendo ───────
                // El punto específico de este test — findByBranchIdForUpdate bloquea las dos
                // filas en una sentencia; si el delete falla, el rollback tiene que deshacer
                // todo, no dejar a courtB (sin reservas) borrada mientras courtA sobrevive.
                assertThat(branchRepository.findById(branchId))
                        .as("La sucursal debe seguir existiendo — el delete no debió pasar")
                        .isPresent();
                assertThat(courtRepository.findById(courtAId))
                        .as("Cancha A (la reservada) debe seguir existiendo")
                        .isPresent();
                assertThat(courtRepository.findById(courtBId))
                        .as("Cancha B (sin reservas) TAMBIÉN debe seguir existiendo — nada de borrado parcial")
                        .isPresent();

                List<ReservationEntity> reservations = reservationRepository.findAll().stream()
                        .filter(r -> r.getCourt().getId().equals(courtAId))
                        .toList();
                assertThat(reservations)
                        .as("Debe existir exactamente la reserva que ganó la carrera")
                        .hasSize(1);
            } else {
                assertThat(bookResult.error()).isInstanceOf(CourtNotFoundException.class);

                // El delete ganó y se comiteó — cascada completa: sucursal y ambas canchas
                // desaparecieron juntas, no solo una de las dos.
                assertThat(branchRepository.findById(branchId))
                        .as("La sucursal ya no debe existir — el delete ganó la carrera")
                        .isEmpty();
                assertThat(courtRepository.findById(courtAId))
                        .as("Cancha A ya no debe existir (cascada Branch→Court)")
                        .isEmpty();
                assertThat(courtRepository.findById(courtBId))
                        .as("Cancha B ya no debe existir (cascada Branch→Court)")
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
