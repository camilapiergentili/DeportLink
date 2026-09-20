package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Construye el escenario de datos común a los tests de concurrencia de esta familia (Owner →
 * Sport → Club → Address → Branch → N canchas [→ Schedule] y N players), para no repetir el
 * mismo bloque de setUp() en cada clase. La lógica específica de cada test (qué reservas arma
 * antes de la carrera, cuántos hilos larga, qué asserta) queda en el test, no acá.
 * <p>
 * {@code label} es solo un prefijo legible para nombres/emails en la base — no aporta unicidad.
 * La unicidad real la sigue dando {@code System.nanoTime()} en cada campo que la necesita, igual
 * que en el setUp() original, así que dos tests usando el mismo label no chocan entre sí.
 */
final class ConcurrencyTestFixtures {

    private final OwnerRepository ownerRepository;
    private final SportRepository sportRepository;
    private final ClubRepository clubRepository;
    private final BranchRepository branchRepository;
    private final CourtRepository courtRepository;
    private final ScheduleRepository scheduleRepository;
    private final PlayerRepository playerRepository;

    ConcurrencyTestFixtures(OwnerRepository ownerRepository,
                             SportRepository sportRepository,
                             ClubRepository clubRepository,
                             BranchRepository branchRepository,
                             CourtRepository courtRepository,
                             ScheduleRepository scheduleRepository,
                             PlayerRepository playerRepository) {
        this.ownerRepository = ownerRepository;
        this.sportRepository = sportRepository;
        this.clubRepository = clubRepository;
        this.branchRepository = branchRepository;
        this.courtRepository = courtRepository;
        this.scheduleRepository = scheduleRepository;
        this.playerRepository = playerRepository;
    }

    /** IDs creados por {@link #createBranchWithCourts}. courtIds.get(0) es la única cancha con Schedule. */
    record BranchFixture(Long branchId, List<Long> courtIds) {
        Long courtId() {
            return courtIds.get(0);
        }
    }

    /**
     * Crea Owner → Sport → Club → Address → Branch → {@code numberOfCourts} canchas.
     * Solo la primera cancha (courtIds.get(0)) recibe un Schedule que cubre {@code day} completo
     * (08:00–22:00, slots de 1h) — las demás quedan sin agenda, igual que courtB en el setup
     * original de BookReservationVsDeleteBranchConcurrencyTest (nunca se reserva, no la necesita).
     *
     * @param label           prefijo legible para nombres en la base (ver javadoc de la clase).
     * @param day             día para el que se crea el Schedule de la primera cancha.
     * @param numberOfCourts  cantidad de canchas a crear en la sucursal (mínimo 1).
     */
    BranchFixture createBranchWithCourts(String label, LocalDate day, int numberOfCourts) {
        OwnerEntity owner = new OwnerEntity();
        owner.setEmail("owner-" + label + "-" + System.nanoTime() + "@example.com");
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
        club.setName("Club " + label);
        club.setLegalName("Club " + label + " " + System.nanoTime());
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
        branch.setName("Sucursal " + label);
        branch.setCancellationWindowHours(12);
        // findByIdForUpdateWithRelations hace JOIN FETCH (INNER) b.address — sin esto,
        // una Branch sin dirección queda excluida del resultado y la cancha "no se encuentra".
        branch.setAddress(address);
        branch = branchRepository.save(branch);

        List<Long> courtIds = new ArrayList<>();
        for (int i = 0; i < numberOfCourts; i++) {
            CourtEntity court = new CourtEntity();
            court.setName("Cancha " + label + " " + (i + 1));
            court.setBranch(branch);
            court.setSport(sport);
            court.setPricePerHour(100.0);
            court = courtRepository.save(court);
            courtIds.add(court.getId());

            if (i == 0) {
                ScheduleEntity schedule = new ScheduleEntity();
                schedule.setDay(day.getDayOfWeek());
                schedule.setOpeningTime(LocalTime.of(8, 0));
                schedule.setClosingTime(LocalTime.of(22, 0));
                schedule.setSlotDuration(Duration.ofHours(1));
                schedule.setCourt(court);
                scheduleRepository.save(schedule);
            }
        }

        return new BranchFixture(branch.getId(), courtIds);
    }

    /** Crea {@code count} players con emails únicos (prefijo {@code label}) y devuelve sus IDs, en orden de creación. */
    List<Long> createPlayers(String label, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    PlayerEntity player = new PlayerEntity();
                    player.setEmail("player-" + label + "-" + System.nanoTime() + "-" + i + "@example.com");
                    player.setPassword("irrelevant");
                    player.setFirstName("Player");
                    player.setLastName(String.valueOf(i));
                    return playerRepository.save(player).getId();
                })
                .toList();
    }
}
