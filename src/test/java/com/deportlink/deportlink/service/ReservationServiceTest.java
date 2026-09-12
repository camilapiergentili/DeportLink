package com.deportlink.deportlink.service;

import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ReservationServiceTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private SportRepository sportRepository;

    @Autowired
    private OwnerRepository ownerRepository;

    private PlayerEntity testPlayer;
    private CourtEntity testCourt;

    @BeforeEach
    public void setUp() {
        // Create test player
        testPlayer = new PlayerEntity();
        testPlayer.setEmail("player@example.com");
        testPlayer.setPassword("password123");
        testPlayer.setFirstName("Test");
        testPlayer.setLastName("Player");
        testPlayer.setPhone("1234567890");
        testPlayer = playerRepository.save(testPlayer);

        // Create test sport
        SportEntity sport = new SportEntity();
        sport.setNameSport("Football");
        sport = sportRepository.save(sport);

        // Create test owner
        OwnerEntity owner = new OwnerEntity();
        owner.setEmail("owner@example.com");
        owner.setPassword("password123");
        owner.setFirstName("Test");
        owner.setLastName("Owner");
        owner.setPhone("1234567890");
        owner.setDni(20123456789L);
        owner.setCuil("20123456789");
        owner.setDateOfBirth(LocalDate.of(1985, 1, 1));
        owner = ownerRepository.save(owner);

        // Create test club
        ClubEntity club = new ClubEntity();
        club.setName("Test Club");
        club.setLegalName("Test Club Legal");
        club.setCuit("30123456789");
        club.getOwners().add(owner);
        club = clubRepository.save(club);

        // Create test branch
        BranchEntity branch = new BranchEntity();
        branch.setClub(club);
        branch.setName("Test Branch");
        branch.setCancellationWindowHours(12);
        branch = branchRepository.save(branch);

        // Create test court
        testCourt = new CourtEntity();
        testCourt.setName("Test Court");
        testCourt.setBranch(branch);
        testCourt.setSport(sport);
        testCourt.setPricePerHour(100.0);
        testCourt = courtRepository.save(testCourt);
    }

    @Test
    public void testReservationRepository_SaveAndFind() {
        ReservationEntity reservation = new ReservationEntity();
        reservation.setPlayer(testPlayer);
        reservation.setCourt(testCourt);
        reservation = reservationRepository.save(reservation);

        assertNotNull(reservation.getId());
        
        ReservationEntity found = reservationRepository.findById(reservation.getId()).orElse(null);
        assertNotNull(found);
        assertEquals(testPlayer.getId(), found.getPlayer().getId());
        assertEquals(testCourt.getId(), found.getCourt().getId());
    }

    @Test
    public void testReservationRepository_Delete() {
        ReservationEntity reservation = new ReservationEntity();
        reservation.setPlayer(testPlayer);
        reservation.setCourt(testCourt);
        reservation = reservationRepository.save(reservation);
        
        long id = reservation.getId();
        reservationRepository.delete(reservation);

        assertTrue(reservationRepository.findById(id).isEmpty());
    }
}
