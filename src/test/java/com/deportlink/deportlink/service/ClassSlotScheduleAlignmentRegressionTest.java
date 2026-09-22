package com.deportlink.deportlink.service;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.usecase.classslot.CreateClassSlotCommand;
import com.deportlink.deportlink.application.usecase.classslot.CreateClassSlotUseCase;
import com.deportlink.deportlink.application.usecase.classslot.PauseClassSlotUseCase;
import com.deportlink.deportlink.application.usecase.classslot.ReactivateClassSlotUseCase;
import com.deportlink.deportlink.application.usecase.reservation.BookReservationUseCase;
import com.deportlink.deportlink.application.usecase.schedule.DeleteScheduleUseCase;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.ClassSlotScheduleMismatchException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.BranchEntity;
import com.deportlink.deportlink.model.entity.ClubEntity;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.InstructorEntity;
import com.deportlink.deportlink.model.entity.OwnerEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.model.entity.ScheduleEntity;
import com.deportlink.deportlink.model.entity.SportEntity;
import com.deportlink.deportlink.persistence.repository.BranchRepository;
import com.deportlink.deportlink.persistence.repository.ClassSlotRepository;
import com.deportlink.deportlink.persistence.repository.ClubRepository;
import com.deportlink.deportlink.persistence.repository.CourtRepository;
import com.deportlink.deportlink.persistence.repository.InstructorRepository;
import com.deportlink.deportlink.persistence.repository.OwnerRepository;
import com.deportlink.deportlink.persistence.repository.PlayerRepository;
import com.deportlink.deportlink.persistence.repository.ScheduleRepository;
import com.deportlink.deportlink.persistence.repository.SportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cierra F17 (docs/software-review-2026-09-09.md — ver docs/loop/F17.md para el análisis y el plan
 * completos) con persistencia real (perfil {@code test}, H2) y wiring real de Spring — sin mocks de
 * {@code ScheduleGateway} — a diferencia de {@code CreateClassSlotUseCaseTest}/
 * {@code ReactivateClassSlotUseCaseTest}, que prueban la lógica del caso de uso de forma aislada.
 * <p>
 * No usa Testcontainers/MySQL: F17 es una laguna de lógica determinística, no una condición de
 * carrera — no hay ninguna afirmación de concurrencia que verificar acá (ver `PLAN` en
 * docs/loop/F17.md sobre por qué esa es la evidencia que corresponde a este hallazgo).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClassSlotScheduleAlignmentRegressionTest {

    @Autowired private CreateClassSlotUseCase createSlot;
    @Autowired private ReactivateClassSlotUseCase reactivateSlot;
    @Autowired private PauseClassSlotUseCase pauseSlot;
    @Autowired private BookReservationUseCase bookReservation;
    @Autowired private DeleteScheduleUseCase deleteSchedule;

    @Autowired private OwnerRepository ownerRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private PlayerRepository playerRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private ClassSlotRepository classSlotRepository;

    private static final Actor ADMIN = new Actor(1L, ActorRole.ADMIN);
    /** Grilla real de la cancha para `dayOfWeek`: 08:00-20:00, turnos de 1h — 14:00 es un turno válido. */
    private static final LocalTime ALIGNED_START = LocalTime.of(14, 0);

    private Long courtId;
    private Long playerId;
    private Long instructorId;
    private DayOfWeek dayOfWeek;
    private LocalDate futureDay;

    @BeforeEach
    void setUp() {
        OwnerEntity owner = new OwnerEntity();
        owner.setEmail("owner-f17@example.com"); owner.setPassword("password123");
        owner.setFirstName("Test"); owner.setLastName("Owner");
        owner.setDni(20123456790L); owner.setCuil("20123456790");
        owner.setDateOfBirth(LocalDate.of(1985, 1, 1));
        owner = ownerRepository.save(owner);

        ClubEntity club = new ClubEntity();
        club.setName("Club F17"); club.setLegalName("Club F17 SRL"); club.setCuit("30199999998");
        club.getOwners().add(owner);
        club = clubRepository.save(club);

        AddressEntity address = new AddressEntity();
        address.setStreetName("Calle Falsa"); address.setNumber(123);
        address.setCity("CABA"); address.setProvince("Buenos Aires");
        address.setLatitude(-34.6); address.setLongitude(-58.4);

        BranchEntity branch = new BranchEntity();
        branch.setName("Sucursal F17"); branch.setClub(club); branch.setCancellationWindowHours(12);
        branch.setAddress(address);
        branch.setVerificationStatus(VerificationStatus.APPROVED);
        branch.setActiveStatus(ActiveStatus.ACTIVE);
        branch = branchRepository.save(branch);

        SportEntity sport = new SportEntity();
        sport.setNameSport("Padel F17");
        sport = sportRepository.save(sport);

        CourtEntity court = new CourtEntity();
        court.setName("Cancha F17"); court.setBranch(branch); court.setSport(sport);
        court.setPricePerHour(1000.0); court.setActiveStatus(ActiveStatus.ACTIVE);
        court = courtRepository.save(court);
        courtId = court.getId();

        futureDay = LocalDate.now().plusWeeks(3);
        dayOfWeek = futureDay.getDayOfWeek();

        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setCourt(court); schedule.setDay(dayOfWeek);
        schedule.setOpeningTime(LocalTime.of(8, 0)); schedule.setClosingTime(LocalTime.of(20, 0));
        schedule.setSlotDuration(Duration.ofHours(1));
        scheduleRepository.save(schedule);

        PlayerEntity player = new PlayerEntity();
        player.setEmail("player-f17@example.com"); player.setPassword("password123");
        player.setFirstName("Test"); player.setLastName("Player");
        player = playerRepository.save(player);
        playerId = player.getId();

        InstructorEntity instructor = new InstructorEntity();
        instructor.setEmail("instructor-f17@example.com"); instructor.setPassword("password123");
        instructor.setFirstName("Test"); instructor.setLastName("Instructor");
        instructor = instructorRepository.save(instructor);
        instructorId = instructor.getId();
    }

    private CreateClassSlotCommand command(LocalTime startTime, Duration duration) {
        return new CreateClassSlotCommand(instructorId, courtId, dayOfWeek, startTime, duration,
                Level.INTERMEDIO, 4);
    }

    @Test
    void altaDeClassSlot_horarioNoAlineadoALaGrilla_esRechazadaSinPersistir() {
        // 14:30 no es múltiplo de 1h desde las 08:00 de la grilla real de esta cancha.
        assertThatThrownBy(() -> createSlot.execute(ADMIN, command(LocalTime.of(14, 30), Duration.ofHours(1))))
                .isInstanceOf(ClassSlotScheduleMismatchException.class);

        assertThat(classSlotRepository.count()).isZero();
    }

    @Test
    void altaDeClassSlot_duracionDistintaDeLaGrilla_esRechazadaSinPersistir() {
        // 14:00 sí es un turno válido, pero la grilla es de 1h, no de 2h.
        assertThatThrownBy(() -> createSlot.execute(ADMIN, command(ALIGNED_START, Duration.ofHours(2))))
                .isInstanceOf(ClassSlotScheduleMismatchException.class);

        assertThat(classSlotRepository.count()).isZero();
    }

    @Test
    void altaDeClassSlot_sinAgendaConfiguradaParaEseDia_esRechazadaSinPersistir() {
        // No hay ninguna fila de Schedule para dayOfWeek.plus(1) en esta cancha (el @BeforeEach solo
        // configuró la agenda de dayOfWeek).
        DayOfWeek dayWithoutSchedule = dayOfWeek.plus(1);
        CreateClassSlotCommand withoutSchedule = new CreateClassSlotCommand(instructorId, courtId,
                dayWithoutSchedule, ALIGNED_START, Duration.ofHours(1), Level.INTERMEDIO, 4);

        assertThatThrownBy(() -> createSlot.execute(ADMIN, withoutSchedule))
                .isInstanceOf(ScheduleNotFoundException.class);

        assertThat(classSlotRepository.count()).isZero();
    }

    @Test
    void altaDeClassSlot_horarioYDuracionAlineados_sePermiteYPersiste() {
        var slot = createSlot.execute(ADMIN, command(ALIGNED_START, Duration.ofHours(1)));

        assertThat(slot.id()).isNotNull();
        assertThat(slot.startTime()).isEqualTo(ALIGNED_START);
        assertThat(classSlotRepository.count()).isEqualTo(1);
    }

    /**
     * El escenario concreto que motivó F17: una {@code Reservation} ya confirmada en un turno de la
     * grilla, y un intento de crear un {@code ClassSlot} con horario/duración NO alineados que la
     * solaparía físicamente. Antes de este fix, nada impedía que ese {@code ClassSlot} se creara
     * (ver ANALYZE en docs/loop/F17.md — reachability confirmada contra el código sin el fix); ahora
     * se rechaza en el alta, antes de que la ClassSession/court_occupancy correspondiente llegue
     * siquiera a evaluarse.
     */
    @Test
    void reservaExistenteEnLaGrilla_yClassSlotDesalineadoQueLaSolaparia_esRechazadaAntesDeCrearse() {
        var reservation = bookReservation.execute(courtId, playerId, futureDay, ALIGNED_START);
        assertThat(reservation.id()).isNotNull();

        // 14:30-15:30 solaparía físicamente la reserva de 14:00-15:00, pero no coincide en
        // start_time exacto con nada — exactamente la laguna que F17 reporta.
        CreateClassSlotCommand overlapping = command(LocalTime.of(14, 30), Duration.ofHours(1));

        assertThatThrownBy(() -> createSlot.execute(ADMIN, overlapping))
                .isInstanceOf(ClassSlotScheduleMismatchException.class);

        assertThat(classSlotRepository.count()).isZero();
    }

    @Test
    void reactivar_conAgendaBorradaMientrasEstabaPausado_esRechazada() {
        var slot = createSlot.execute(ADMIN, command(ALIGNED_START, Duration.ofHours(1)));
        pauseSlot.execute(ADMIN, slot.id());

        var schedule = scheduleRepository.findByCourtIdAndDay(courtId, dayOfWeek).orElseThrow();
        deleteSchedule.execute(schedule.getId(), courtId);

        assertThatThrownBy(() -> reactivateSlot.execute(ADMIN, slot.id()))
                .isInstanceOf(ScheduleNotFoundException.class);

        assertThat(classSlotRepository.findById(slot.id()).orElseThrow().getActiveStatus())
                .isEqualTo(ActiveStatus.INACTIVE);
    }
}
