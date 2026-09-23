package com.deportlink.deportlink.config;

import com.deportlink.deportlink.application.usecase.branch.ApproveBranchUseCase;
import com.deportlink.deportlink.application.usecase.branch.CreateBranchUseCase;
import com.deportlink.deportlink.application.usecase.club.ApproveClubUseCase;
import com.deportlink.deportlink.application.usecase.club.CreateClubUseCase;
import com.deportlink.deportlink.application.usecase.court.CreateCourtUseCase;
import com.deportlink.deportlink.application.usecase.owner.RegisterOwnerUseCase;
import com.deportlink.deportlink.application.usecase.player.RegisterPlayerUseCase;
import com.deportlink.deportlink.application.usecase.schedule.AddScheduleUseCase;
import com.deportlink.deportlink.application.usecase.sport.CreateSportUseCase;
import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.model.Owner;
import com.deportlink.deportlink.dto.request.AddressRequestDto;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.request.ScheduleRequestDto;
import com.deportlink.deportlink.enums.ClubType;
import com.deportlink.deportlink.model.Rol;
import com.deportlink.deportlink.model.entity.UserEntity;
import com.deportlink.deportlink.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Carga un set fijo de datos de demo — admin, owner, player, club, sucursal, cancha, agenda —
 * reutilizando los mismos casos de uso que expone la API, para que los datos respeten las mismas
 * reglas de negocio que cualquier alta hecha a mano (club aprobado antes de tener sucursales,
 * sucursal aprobada y activa antes de tener canchas, etc.).
 * <p>
 * Perfil {@code dev} únicamente — nunca corre en staging/producción (ver README, sección
 * "Perfil default vs. perfil dev"). {@link DevDataSeederRunner} es quien lo dispara al arrancar.
 * <p>
 * Único método transaccional: si algo falla a mitad de camino, no queda nada a medias — la
 * próxima vez que se levante la app con el perfil {@code dev}, {@link #alreadySeeded()} sigue
 * viendo que el admin no existe y reintenta desde cero.
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder {

    public static final String ADMIN_EMAIL = "admin@deportlink.demo";
    public static final String OWNER_EMAIL = "owner@deportlink.demo";
    public static final String PLAYER_EMAIL = "player@deportlink.demo";
    public static final String DEMO_PASSWORD = "Demo1234";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegisterOwnerUseCase registerOwnerUseCase;
    private final RegisterPlayerUseCase registerPlayerUseCase;
    private final CreateSportUseCase createSportUseCase;
    private final CreateClubUseCase createClubUseCase;
    private final ApproveClubUseCase approveClubUseCase;
    private final CreateBranchUseCase createBranchUseCase;
    private final ApproveBranchUseCase approveBranchUseCase;
    private final CreateCourtUseCase createCourtUseCase;
    private final AddScheduleUseCase addScheduleUseCase;

    public boolean alreadySeeded() {
        return userRepository.findByEmail(ADMIN_EMAIL).isPresent();
    }

    @Transactional
    public void seed() {
        if (alreadySeeded()) {
            log.info("Dev data seed: {} ya existe, no se vuelve a cargar nada.", ADMIN_EMAIL);
            return;
        }
        log.info("Dev data seed: cargando datos de demo...");

        // ADMIN: sin caso de uso propio — no hay ningún endpoint que cree un ADMIN (el primero
        // no puede auto-registrarse, y no es un caso de uso menor agregar uno solo para esto).
        // Sin subtipo de entidad propio (a diferencia de Owner/Player/Instructor), así que un
        // UserEntity liso alcanza.
        UserEntity admin = new UserEntity(null, "Admin", "Demo", ADMIN_EMAIL,
                passwordEncoder.encode(DEMO_PASSWORD), null, Rol.ADMIN);
        userRepository.save(admin);

        Owner owner = registerOwnerUseCase.execute(ownerRequest());
        registerPlayerUseCase.execute(playerRequest());

        var sport = createSportUseCase.execute("Pádel");

        // approve() ya deja el club/sucursal ACTIVE en el mismo paso (ver Club.approve() y
        // Branch.approve()) — no hace falta un activate*UseCase aparte acá.
        Club club = createClubUseCase.execute("Club Demo", "Club Demo S.A.", "30-71234567-8",
                ClubType.SA, Set.of(owner.id()));
        approveClubUseCase.execute(club.id());

        var branch = createBranchUseCase.execute("Sucursal Centro",
                new Address("Av. Siempreviva", 742, "Springfield", "Buenos Aires", 1900, -34.6, -58.4),
                club.id(), 2);
        approveBranchUseCase.execute(branch.id());

        Court court = createCourtUseCase.execute("Cancha 1", 5000.0, branch.id(), sport.id());

        addScheduleUseCase.execute(court.id(), weekSchedule());

        log.info("Dev data seed: listo. Admin={} / Owner={} / Player={} / password para los tres: {}",
                ADMIN_EMAIL, OWNER_EMAIL, PLAYER_EMAIL, DEMO_PASSWORD);
    }

    private static OwnerRequestDto ownerRequest() {
        OwnerRequestDto dto = new OwnerRequestDto();
        dto.setFirstName("Dueño");
        dto.setLastName("Demo");
        dto.setEmail(OWNER_EMAIL);
        dto.setPassword(DEMO_PASSWORD);
        dto.setConfirmPassword(DEMO_PASSWORD);
        dto.setDni(30712345);
        dto.setCuil("20-30712345-6");
        dto.setDateOfBirth("01/01/1985");
        return dto;
    }

    private static PlayerRequestDto playerRequest() {
        PlayerRequestDto dto = new PlayerRequestDto();
        dto.setFirstName("Jugador");
        dto.setLastName("Demo");
        dto.setEmail(PLAYER_EMAIL);
        dto.setPassword(DEMO_PASSWORD);
        dto.setConfirmPassword(DEMO_PASSWORD);
        AddressRequestDto address = new AddressRequestDto();
        address.setStreetName("Av. Siempreviva");
        address.setNumber(742);
        address.setCity("Springfield");
        address.setProvince("Buenos Aires");
        address.setPostalCode(1900);
        dto.setAddressRequestDto(address);
        return dto;
    }

    /** Lunes a domingo, 08:00–22:00, turnos de 1h — mismo formato que exige AddScheduleUseCase. */
    private static List<ScheduleRequestDto> weekSchedule() {
        return Arrays.stream(DayOfWeek.values()).map(day -> {
            ScheduleRequestDto dto = new ScheduleRequestDto();
            dto.setDay(day.name());
            dto.setOpeningTime("08:00");
            dto.setClosingTime("22:00");
            dto.setSlotDuration(60L);
            return dto;
        }).toList();
    }
}
