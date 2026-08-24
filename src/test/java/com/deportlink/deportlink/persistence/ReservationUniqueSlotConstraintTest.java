package com.deportlink.deportlink.persistence;

import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica el índice único de V2__add_unique_reservation_slot.sql directamente contra MySQL
 * real — a diferencia de los demás tests de esta familia (BookReservationConcurrencyTest y
 * afines), acá SÍ dejamos Flyway habilitado y ddl-auto=validate en vez de create-drop: la
 * restricción que se prueba vive en la migración (columna generada + UNIQUE), no en el mapeo
 * de ReservationEntity — create-drop, generado desde las entidades, jamás la crearía.
 * <p>
 * No es un test de concurrencia (inserciones secuenciales alcanzan) — es la confirmación de
 * que la defensa en profundidad a nivel de base de datos efectivamente defiende algo.
 */
@Testcontainers
@SpringBootTest
class ReservationUniqueSlotConstraintTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.23")
            .withDatabaseName("deportlink_unique_slot")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LWxvbmctZW5vdWdoLWZvci1qd3Qtc2lnbmluZw==");
        registry.add("jwt.expiration", () -> "36000000");
        registry.add("cors.allowed-origins", () -> "http://localhost:5173");
        // spring.flyway.enabled deliberadamente NO se sobreescribe a false acá — con
        // flyway-core en el classpath y sin el perfil "test" activo, corre con su default
        // (true) y aplica V1 + V2 de verdad contra el contenedor vacío.
    }

    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private ReservationRepository reservationRepository;

    private Long courtId;
    private Long playerId;
    private LocalDate day;
    private LocalTime startTime;

    @BeforeEach
    void setUp() {
        OwnerEntity owner = new OwnerEntity();
        owner.setEmail("owner-unique-slot-" + System.nanoTime() + "@example.com");
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
        club.setName("Club Unique Slot");
        club.setLegalName("Club Unique Slot " + System.nanoTime());
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
        branch.setName("Sucursal Unique Slot");
        branch.setAddress(address);
        branch = branchRepository.save(branch);

        CourtEntity court = new CourtEntity();
        court.setName("Cancha Unique Slot");
        court.setBranch(branch);
        court.setSport(sport);
        court.setPricePerHour(100.0);
        courtId = courtRepository.save(court).getId();

        PlayerEntity player = new PlayerEntity();
        player.setEmail("player-unique-slot-" + System.nanoTime() + "@example.com");
        player.setPassword("irrelevant");
        player.setFirstName("Player");
        player.setLastName("One");
        playerId = playerRepository.save(player).getId();

        day = LocalDate.now().plusDays(7);
        startTime = LocalTime.of(10, 0);
    }

    private ReservationEntity reservation(StatusReservation status) {
        ReservationEntity r = new ReservationEntity();
        r.setCourt(courtRepository.getReferenceById(courtId));
        r.setPlayer(playerRepository.getReferenceById(playerId));
        r.setDay(day);
        r.setStartTime(startTime);
        r.setDuration(Duration.ofHours(1));
        r.setStatus(status);
        return r;
    }

    @Test
    void dosReservasRESERVADOMismoSlot_violaElIndiceUnico() {
        reservationRepository.saveAndFlush(reservation(StatusReservation.RESERVADO));

        assertThatThrownBy(() -> reservationRepository.saveAndFlush(reservation(StatusReservation.RESERVADO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void variasReservasCanceladasMismoSlot_noViolaNada() {
        // El caso que el índice tiene que permitir explícitamente: historial de cancelaciones
        // sobre el mismo horario no debe bloquear nada — solo importa lo que está RESERVADO.
        assertThatCode(() -> {
            reservationRepository.saveAndFlush(reservation(StatusReservation.CANCELADO));
            reservationRepository.saveAndFlush(reservation(StatusReservation.CANCELADO));
            reservationRepository.saveAndFlush(reservation(StatusReservation.CANCELADO));
        }).doesNotThrowAnyException();
    }

    @Test
    void reservaCanceladaYLuegoUnaNuevaRESERVADOEnElMismoSlot_noViolaNada() {
        // El escenario real que motivó la columna generada en vez de un UNIQUE simple:
        // alguien reservó, canceló, y otro jugador reserva el mismo horario después.
        assertThatCode(() -> {
            reservationRepository.saveAndFlush(reservation(StatusReservation.CANCELADO));
            reservationRepository.saveAndFlush(reservation(StatusReservation.RESERVADO));
        }).doesNotThrowAnyException();
    }
}
