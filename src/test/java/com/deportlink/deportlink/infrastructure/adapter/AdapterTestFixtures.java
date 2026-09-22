package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;

import java.time.LocalDate;

/**
 * Construye el escenario de datos común a los tests de Nivel A del módulo de clases (Owner →
 * Sport → Club → Branch → Court → Instructor → Player), para no repetir el mismo setUp() en cada
 * test class — mismo espíritu que ConcurrencyTestFixtures para los tests de concurrencia de
 * Reservation.
 */
final class AdapterTestFixtures {

    private final OwnerRepository ownerRepository;
    private final SportRepository sportRepository;
    private final ClubRepository clubRepository;
    private final BranchRepository branchRepository;
    private final CourtRepository courtRepository;
    private final InstructorRepository instructorRepository;
    private final PlayerRepository playerRepository;

    AdapterTestFixtures(OwnerRepository ownerRepository, SportRepository sportRepository,
                         ClubRepository clubRepository, BranchRepository branchRepository,
                         CourtRepository courtRepository, InstructorRepository instructorRepository,
                         PlayerRepository playerRepository) {
        this.ownerRepository = ownerRepository;
        this.sportRepository = sportRepository;
        this.clubRepository = clubRepository;
        this.branchRepository = branchRepository;
        this.courtRepository = courtRepository;
        this.instructorRepository = instructorRepository;
        this.playerRepository = playerRepository;
    }

    CourtEntity createCourt(String label) {
        OwnerEntity owner = new OwnerEntity();
        owner.setEmail("owner-" + label + "-" + System.nanoTime() + "@example.com");
        owner.setPassword("irrelevant");
        owner.setFirstName("Test");
        owner.setLastName("Owner");
        owner.setDni(20123456789L);
        owner.setCuil("cuil-" + label + "-" + System.nanoTime());
        owner.setDateOfBirth(LocalDate.of(1985, 1, 1));
        owner = ownerRepository.save(owner);

        ClubEntity club = new ClubEntity();
        club.setName("Club " + label);
        club.setLegalName("Club " + label + " " + System.nanoTime());
        club.setCuit("cuit-" + label + "-" + System.nanoTime());
        club.getOwners().add(owner);
        club = clubRepository.save(club);

        BranchEntity branch = new BranchEntity();
        branch.setClub(club);
        branch.setName("Sucursal " + label);
        branch.setCancellationWindowHours(12);
        branch = branchRepository.save(branch);

        SportEntity sport = new SportEntity();
        sport.setNameSport("Pádel");
        sport = sportRepository.save(sport);

        CourtEntity court = new CourtEntity();
        court.setName("Cancha " + label);
        court.setBranch(branch);
        court.setSport(sport);
        court.setPricePerHour(1000.0);
        return courtRepository.save(court);
    }

    InstructorEntity createInstructor(String label) {
        InstructorEntity instructor = new InstructorEntity();
        instructor.setEmail("instructor-" + label + "-" + System.nanoTime() + "@example.com");
        instructor.setPassword("irrelevant");
        instructor.setFirstName("Instructor");
        instructor.setLastName(label);
        return instructorRepository.save(instructor);
    }

    PlayerEntity createPlayer(String label) {
        PlayerEntity player = new PlayerEntity();
        player.setEmail("player-" + label + "-" + System.nanoTime() + "@example.com");
        player.setPassword("irrelevant");
        player.setFirstName("Player");
        player.setLastName(label);
        return playerRepository.save(player);
    }
}
