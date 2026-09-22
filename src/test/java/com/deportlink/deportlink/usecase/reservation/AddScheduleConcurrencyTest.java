package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.usecase.schedule.AddScheduleUseCase;
import com.deportlink.deportlink.dto.request.ScheduleRequestDto;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.ScheduleAlreadyExistsException;
import com.deportlink.deportlink.model.entity.BranchEntity;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.ScheduleEntity;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Test de concurrencia contra MySQL REAL (Testcontainers) para F16
 * (docs/software-review-2026-08-31.md) — verifica que N altas de horario simultáneas para la
 * misma cancha, con rangos que se solapan entre sí, nunca resulten en más de una fila de
 * {@code availability} para el mismo (court_id, day_of_week). Antes del fix (lock pesimista sobre
 * Court en AddScheduleUseCase), esto era alcanzable: dos hilos podían leer el mismo estado de
 * `availability` antes de que cualquiera insertara, pasar juntos filterConflicts() e insertar dos
 * filas — lo que rompe con 500 (IncorrectResultSizeDataAccessException) cualquier llamada
 * posterior a findByCourtIdAndDay (Optional, espera 0 o 1 fila). Ver
 * AddScheduleUseCaseTest para la verificación (Mockito) del orden de invocación — este test prueba
 * que el lock realmente serializa transacciones concurrentes, cosa que Mockito no puede probar.
 * <p>
 * Mismo setup que BookReservationConcurrencyTest (MySQL real vía Testcontainers, sin perfil
 * "test"/H2 ni Flyway — H2 no reproduce fielmente PESSIMISTIC_WRITE/SELECT ... FOR UPDATE).
 */
@Testcontainers
@SpringBootTest
// Close the Spring context while the class-scoped MySQL container is still running.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AddScheduleConcurrencyTest {

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
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LWxvbmctZW5vdWdoLWZvci1qd3Qtc2lnbmluZw==");
        registry.add("jwt.expiration", () -> "36000000");
        registry.add("cors.allowed-origins", () -> "http://localhost:5173");
    }

    private static final int CONCURRENT_REQUESTS = 10;

    @Autowired private AddScheduleUseCase addScheduleUseCase;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private PlayerRepository playerRepository;

    private Long courtId;
    private LocalDate day;
    private DayOfWeek dayOfWeek;

    @BeforeEach
    void setUp() {
        var fixtures = new ConcurrencyTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, scheduleRepository, playerRepository);

        day = LocalDate.now().plusDays(7);
        dayOfWeek = day.getDayOfWeek();

        // numberOfCourts=2: courtIds.get(0) recibe un Schedule de la propia fixture (irrelevante
        // acá); courtIds.get(1) queda SIN agenda — es la que usa este test, para no arrancar ya
        // en el estado de conflicto que filterConflicts() detectaría igual sin ninguna carrera.
        var branch = fixtures.createBranchWithCourts("addschedule", day, 2);
        courtId = branch.courtIds().get(1);

        // ConcurrencyTestFixtures no fija activeStatus/verificationStatus (ningún otro test de
        // esta familia los necesita: Book/Reschedule/Delete no llaman a Court.isActive() ni a
        // Branch.isApproved()). AddScheduleUseCase sí los chequea antes de tomar el lock — se
        // activan acá, en este test, en vez de tocar la fixture compartida.
        BranchEntity branchEntity = branchRepository.findById(branch.branchId()).orElseThrow();
        branchEntity.setVerificationStatus(VerificationStatus.APPROVED);
        branchEntity.setActiveStatus(ActiveStatus.ACTIVE);
        branchRepository.save(branchEntity);

        CourtEntity courtEntity = courtRepository.findById(courtId).orElseThrow();
        courtEntity.setActiveStatus(ActiveStatus.ACTIVE);
        courtRepository.save(courtEntity);
    }

    @RepeatedTest(5)
    void execute_diezAltasSimultaneasConHorarioSolapado_ganaExactamenteUna() throws InterruptedException {
        int n = CONCURRENT_REQUESTS;
        ExecutorService executor = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<CompletableFuture<Result>> futures = java.util.stream.IntStream.range(0, n)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        awaitUninterruptibly(start);
                        try {
                            // Mismo día, mismo rango horario para las N ejecuciones: cualquier
                            // par que corra sin serializarse debería detectar solapamiento entre
                            // sí — si el lock funciona, como mucho una lo logra.
                            addScheduleUseCase.execute(courtId, List.of(scheduleDto()));
                            return Result.ok();
                        } catch (Exception ex) {
                            return Result.failure(ex);
                        }
                    }, executor))
                    .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("Los %d hilos deben quedar listos antes de largarlos todos juntos", n)
                    .isTrue();
            start.countDown();

            List<Result> results = futures.stream().map(CompletableFuture::join).toList();

            long successes = results.stream().filter(Result::success).count();
            long alreadyExistsFailures = results.stream()
                    .filter(r -> !r.success())
                    .filter(r -> r.error() instanceof ScheduleAlreadyExistsException)
                    .count();
            long otherFailures = results.stream()
                    .filter(r -> !r.success())
                    .filter(r -> !(r.error() instanceof ScheduleAlreadyExistsException))
                    .count();

            assertThat(otherFailures)
                    .as("Ninguna ejecución debería fallar con algo distinto de ScheduleAlreadyExistsException"
                            + " (en particular, ninguna IncorrectResultSizeDataAccessException)")
                    .isZero();
            assertThat(successes)
                    .as("Exactamente una de las %d altas concurrentes debe insertar su horario", n)
                    .isEqualTo(1);
            assertThat(alreadyExistsFailures)
                    .as("Las %d restantes deben fallar con ScheduleAlreadyExistsException", n - 1)
                    .isEqualTo(n - 1);

            // No confiamos solo en el resultado de los hilos: lo confirmamos contra la base real.
            List<ScheduleEntity> rows = scheduleRepository.findByCourtId(courtId).stream()
                    .filter(s -> s.getDay() == dayOfWeek)
                    .toList();
            assertThat(rows)
                    .as("Debe existir EXACTAMENTE una fila de availability para ese court/day")
                    .hasSize(1);

            // La consecuencia real de F16 (lo que rompía antes del fix): una llamada posterior a
            // findByCourtIdAndDay para esta cancha/día no debe lanzar
            // IncorrectResultSizeDataAccessException.
            assertThatCode(() -> scheduleRepository.findByCourtIdAndDay(courtId, dayOfWeek))
                    .as("findByCourtIdAndDay no debe romper después de la carrera")
                    .doesNotThrowAnyException();
        } finally {
            executor.shutdownNow();
        }
    }

    private ScheduleRequestDto scheduleDto() {
        ScheduleRequestDto dto = new ScheduleRequestDto();
        dto.setDay(dayOfWeek.name());
        dto.setOpeningTime("10:00");
        dto.setClosingTime("14:00");
        dto.setSlotDuration(60L);
        return dto;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private record Result(boolean success, Throwable error) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result failure(Throwable error) {
            return new Result(false, error);
        }
    }
}
