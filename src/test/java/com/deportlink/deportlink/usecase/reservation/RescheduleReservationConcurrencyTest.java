package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.usecase.reservation.BookReservationUseCase;
import com.deportlink.deportlink.application.usecase.reservation.RescheduleReservationUseCase;
import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.exception.ReservationNotFoundException;
import com.deportlink.deportlink.exception.SlotNotAvailableException;
import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Equivalente a {@link BookReservationConcurrencyTest} pero para
 * {@link RescheduleReservationUseCase} — usa el mismo mecanismo de lock
 * (courtGateway.findByIdForUpdate) antes de verificar disponibilidad, así que está expuesto
 * al mismo riesgo de carrera: dos jugadores reprogramando sus reservas existentes hacia el
 * MISMO turno nuevo, al mismo tiempo.
 * <p>
 * Mismo setup de Testcontainers que BookReservationConcurrencyTest — ver esa clase para el
 * razonamiento de por qué no se usa el perfil "test" (H2) ni Flyway acá.
 */
@Testcontainers
@SpringBootTest
class RescheduleReservationConcurrencyTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.23")
            .withDatabaseName("deportlink_concurrency_reschedule")
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
        // No hay perfil "test" activo acá (a propósito, ver el javadoc de la clase), así que
        // este placeholder de SecurityConfig no lo resuelve application-test.properties.
        registry.add("cors.allowed-origins", () -> "http://localhost:5173");
    }

    @Autowired private BookReservationUseCase bookReservationUseCase;
    @Autowired private RescheduleReservationUseCase rescheduleReservationUseCase;
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
    private LocalTime newStartTime;
    private Long reservationIdPlayer1;
    private Long reservationIdPlayer2;
    private Long playerId1;
    private Long playerId2;

    @BeforeEach
    void setUp() {
        var fixtures = new ConcurrencyTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, scheduleRepository, playerRepository);

        day = LocalDate.now().plusDays(7);
        newStartTime = LocalTime.of(15, 0); // turno nuevo que ambos van a disputar

        var branch = fixtures.createBranchWithCourts("reschedule", day, 1);
        courtId = branch.courtId();

        List<Long> players = fixtures.createPlayers("reschedule", 2);
        playerId1 = players.get(0);
        playerId2 = players.get(1);

        // Cada jugador arranca con su propia reserva RESERVADO, en horarios distintos entre sí
        // y distintos del turno nuevo que van a disputar — así el único conflicto real es el
        // turno destino de la reprogramación.
        Reservation r1 = bookReservationUseCase.execute(courtId, playerId1, day, LocalTime.of(9, 0));
        Reservation r2 = bookReservationUseCase.execute(courtId, playerId2, day, LocalTime.of(11, 0));
        reservationIdPlayer1 = r1.id();
        reservationIdPlayer2 = r2.id();
    }

    @Test
    void execute_dosReprogramacionesSimultaneasAlMismoTurno_ganaExactamenteUna() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            CompletableFuture<Result> future1 = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                awaitUninterruptibly(start);
                try {
                    var r = rescheduleReservationUseCase.execute(reservationIdPlayer1, playerId1, day, newStartTime);
                    return Result.success(r.id());
                } catch (Exception ex) {
                    return Result.failure(ex);
                }
            }, executor);

            CompletableFuture<Result> future2 = CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                awaitUninterruptibly(start);
                try {
                    var r = rescheduleReservationUseCase.execute(reservationIdPlayer2, playerId2, day, newStartTime);
                    return Result.success(r.id());
                } catch (Exception ex) {
                    return Result.failure(ex);
                }
            }, executor);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Result> results = List.of(future1.join(), future2.join());

            long successes = results.stream().filter(Result::success).count();
            long slotNotAvailableFailures = results.stream()
                    .filter(r -> !r.success())
                    .filter(r -> r.error() instanceof SlotNotAvailableException)
                    .count();

            assertThat(successes)
                    .as("Exactamente una de las 2 reprogramaciones concurrentes debe ganar el turno nuevo")
                    .isEqualTo(1);
            assertThat(slotNotAvailableFailures)
                    .as("La otra debe fallar con SlotNotAvailableException")
                    .isEqualTo(1);

            // Confirmación directa contra la base: exactamente una fila RESERVADO en el turno nuevo.
            List<ReservationEntity> reservedAtNewSlot = reservationRepository.findAll().stream()
                    .filter(r -> r.getCourt().getId().equals(courtId))
                    .filter(r -> r.getDay().equals(day))
                    .filter(r -> r.getStartTime().equals(newStartTime))
                    .filter(r -> r.getStatus() == StatusReservation.RESERVADO)
                    .toList();

            assertThat(reservedAtNewSlot)
                    .as("Debe existir EXACTAMENTE una fila RESERVADO en la base para el turno nuevo disputado")
                    .hasSize(1);

            // La reserva original del que ganó pasó a REPROGRAMADO; la del que perdió el turno
            // nuevo debe seguir RESERVADO en su horario original — la reprogramación fallida no
            // debe haber tocado su reserva vieja.
            boolean player1Won = results.get(0).success();
            Long winnerOriginalReservationId = player1Won ? reservationIdPlayer1 : reservationIdPlayer2;
            Long loserOriginalReservationId = player1Won ? reservationIdPlayer2 : reservationIdPlayer1;

            ReservationEntity winnerOriginal = reservationRepository.findById(winnerOriginalReservationId).orElseThrow();
            ReservationEntity loserOriginal = reservationRepository.findById(loserOriginalReservationId).orElseThrow();

            assertThat(winnerOriginal.getStatus())
                    .as("La reserva original del jugador que ganó el turno nuevo debe quedar REPROGRAMADO")
                    .isEqualTo(StatusReservation.REPROGRAMADO);
            assertThat(loserOriginal.getStatus())
                    .as("La reserva original del jugador que perdió la carrera no debe haberse tocado")
                    .isEqualTo(StatusReservation.RESERVADO);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * El lock temprano (findCourtIdByReservationForUpdate, primera lectura de la transacción)
     * no debe cambiar el comportamiento de IDOR: un jugador intentando reprogramar la
     * reservationId de otro sigue sin encontrar nada — ni antes ni después del fix se filtra
     * que la reserva existe y es de otra persona.
     */
    @Test
    void execute_intentaReprogramarReservaDeOtroJugador_lanzaReservationNotFoundException() {
        assertThatThrownBy(() ->
                rescheduleReservationUseCase.execute(reservationIdPlayer1, playerId2, day, newStartTime))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessage("No se encontró la reserva");

        // El intento fallido (aunque ya tomó y liberó el lock de la cancha) no debe haber
        // tocado la reserva original de player1.
        ReservationEntity untouched = reservationRepository.findById(reservationIdPlayer1).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(StatusReservation.RESERVADO);
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
