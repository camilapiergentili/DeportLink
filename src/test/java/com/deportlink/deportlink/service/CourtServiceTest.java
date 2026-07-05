package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CourtServiceTest {

    @Autowired
    private CourtService courtService;

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

    @BeforeEach
    public void setUp() {
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

        // Create test sport
        SportEntity sport = new SportEntity();
        sport.setNameSport("Football");
        sport = sportRepository.save(sport);

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
        branch = branchRepository.save(branch);

        // Create test court
        CourtEntity court = new CourtEntity();
        court.setName("Test Court");
        court.setBranch(branch);
        court.setSport(sport);
        court.setPricePerHour(100.0);
        courtRepository.save(court);
    }

    @Test
    public void testGetByIdResponse_Success() {
        List<CourtEntity> courts = courtRepository.findAll();
        assertTrue(courts.size() > 0);
        
        CourtEntity testCourt = courts.get(0);
        CourtResponseDto response = courtService.getByIdResponse(testCourt.getId());
        
        assertNotNull(response);
        assertEquals("Test Court", response.getName());
    }

    @Test
    public void testGetByIdResponse_NotFound() {
        assertThrows(CourtNotFoundException.class, () -> courtService.getByIdResponse(99999L));
    }

    @Test
    public void testGetAllActiveAndApproved_Success() {
        List<CourtResponseDto> courts = courtService.getAllActiveAndApproved();
        assertNotNull(courts);
    }
}

