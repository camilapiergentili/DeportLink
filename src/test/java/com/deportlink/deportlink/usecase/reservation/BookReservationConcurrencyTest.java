package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.usecase.reservation.BookReservationUseCase;
import com.deportlink.deportlink.enums.StatusReservation;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de concurrencia contra MySQL REAL (Testcontainers) — verifica la garantía central del
 * sistema de reservas: N requests simultáneas para el mismo court+day+startTime nunca deben
 * resultar en más de una fila RESERVADO. Los tests de {@link BookReservationUseCaseTest} usan
 * Mockito y solo verifican el ORDEN de invocación (findByIdForUpdate antes que
 * findBookedSlots) — no prueban que el lock realmente serialice transacciones concurrentes.
 * H2 no reproduce fielmente el locking de InnoDB (PESSIMISTIC_WRITE / SELECT ... FOR UPDATE),
 * así que esto necesita un motor real.
 * <p>
 * Deliberadamente NO usa el perfil "test" (H2) ni Flyway: es un contenedor descartable de un
 * solo uso, así que Hibernate crea el schema directo (ddl-auto=create-drop) y
 * {@code @DynamicPropertySource} sobreescribe todo lo que
 * {@code src/test/resources/application.properties} fija para H2 (datasource + dialecto),
 * que de otro modo tomaría precedencia por perfil.
 */
@Testcontainers
@SpringBootTest
class BookReservationConcurrencyTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.23")
            .withDatabaseName("deportlink_concurrency")
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
        // N hilos + el hilo de test necesitan conexión propia simultánea — el pool default (10)
        // alcanzaría justo para N=10, pero lo subimos para no confundir "esperando conexión"
        // con "esperando el lock pesimista", que es lo único que nos interesa medir acá.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LWxvbmctZW5vdWdoLWZvci1qd3Qtc2lnbmluZw==");
        registry.add("jwt.expiration", () -> "36000000");
        // No hay perfil "test" activo acá (a propósito, ver el javadoc de la clase), así que
        // este placeholder de SecurityConfig no lo resuelve application-test.properties.
        registry.add("cors.allowed-origins", () -> "http://localhost:5173");
    }

    private static final int CONCURRENT_PLAYERS = 10;

    @Autowired private BookReservationUseCase bookReservationUseCase;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private ReservationRepository reservationRepository;

    private Long courtId;
    private LocalDate day;
    private LocalTime startTime;
    private List<Long> playerIds;

    @BeforeEach
    void setUp() {
        OwnerEntity owner = new OwnerEntity();
        owner.setEmail("owner-concurrency-" + System.nanoTime() + "@example.com");
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
        club.setName("Club Concurrencia");
        club.setLegalName("Club Concurrencia " + System.nanoTime());
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
        branch.setName("Sucursal Concurrencia");
        // findByIdForUpdateWithRelations hace JOIN FETCH (INNER) b.address — sin esto,
        // una Branch sin dirección queda excluida del resultado y la cancha "no se encuentra".
        branch.setAddress(address);
        branch = branchRepository.save(branch);

        CourtEntity court = new CourtEntity();
        court.setName("Cancha Concurrencia");
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

        playerIds = IntStream.range(0, CONCURRENT_PLAYERS)
                .mapToObj(i -> {
                    PlayerEntity player = new PlayerEntity();
                    player.setEmail("player-concurrency-" + System.nanoTime() + "-" + i + "@example.com");
                    player.setPassword("irrelevant");
                    player.setFirstName("Player");
                    player.setLastName(String.valueOf(i));
                    return playerRepository.save(player).getId();
                })
                .toList();
    }

    @RepeatedTest(5)
    void execute_diezReservasSimultaneasParaElMismoSlot_ganaExactamenteUna() throws InterruptedException {
        int n = playerIds.size();
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<CompletableFuture<Result>> futures = playerIds.stream()
                    .map(playerId -> CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        awaitUninterruptibly(start);
                        try {
                            var reservation = bookReservationUseCase.execute(courtId, playerId, day, startTime);
                            return Result.success(reservation.id());
                        } catch (Exception ex) {
                            return Result.failure(ex);
                        }
                    }, executor))
                    .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("Los %d hilos deben quedar listos antes de largarlos todos juntos", n)
                    .isTrue();
            start.countDown(); // largamos los N hilos lo más simultáneamente posible

            List<Result> results = futures.stream().map(CompletableFuture::join).toList();

            long successes = results.stream().filter(Result::success).count();
            long slotNotAvailableFailures = results.stream()
                    .filter(r -> !r.success())
                    .filter(r -> r.error() instanceof SlotNotAvailableException)
                    .count();
            long otherFailures = results.stream()
                    .filter(r -> !r.success())
                    .filter(r -> !(r.error() instanceof SlotNotAvailableException))
                    .count();

            assertThat(otherFailures)
                    .as("Ninguna ejecución debería fallar con algo distinto de SlotNotAvailableException")
                    .isZero();
            assertThat(successes)
                    .as("Exactamente una de las %d reservas concurrentes debe ganar el slot", n)
                    .isEqualTo(1);
            assertThat(slotNotAvailableFailures)
                    .as("Las %d restantes deben fallar con SlotNotAvailableException", n - 1)
                    .isEqualTo(n - 1);

            // No confiamos solo en el resultado de los hilos: lo confirmamos contra la base real.
            List<ReservationEntity> reservedRows = reservationRepository.findAll().stream()
                    .filter(r -> r.getCourt().getId().equals(courtId))
                    .filter(r -> r.getDay().equals(day))
                    .filter(r -> r.getStartTime().equals(startTime))
                    .filter(r -> r.getStatus() == StatusReservation.RESERVADO)
                    .toList();

            assertThat(reservedRows)
                    .as("Debe existir EXACTAMENTE una fila RESERVADO en la base para ese court/day/startTime")
                    .hasSize(1);
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

    private record Result(boolean success, Long reservationId, Throwable error) {
        static Result success(Long reservationId) {
            return new Result(true, reservationId, null);
        }

        static Result failure(Throwable error) {
            return new Result(false, null, error);
        }
    }
}
