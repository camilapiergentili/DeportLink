package com.deportlink.deportlink.service;

import com.deportlink.deportlink.application.usecase.court.*;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CourtServiceTest {

    @Autowired private GetCourtByIdUseCase getCourtByIdUseCase;
    @Autowired private GetApprovedCourtsUseCase getApprovedCourtsUseCase;

    @Autowired private CourtRepository courtRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private OwnerRepository ownerRepository;

    private CourtEntity testCourt;

    @BeforeEach
    public void setUp() {
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

        SportEntity sport = new SportEntity();
        sport.setNameSport("Football");
        sport = sportRepository.save(sport);

        ClubEntity club = new ClubEntity();
        club.setName("Test Club");
        club.setLegalName("Test Club Legal");
        club.setCuit("30123456789");
        club.getOwners().add(owner);
        club = clubRepository.save(club);

        BranchEntity branch = new BranchEntity();
        branch.setClub(club);
        branch.setName("Test Branch");
        branch = branchRepository.save(branch);

        testCourt = new CourtEntity();
        testCourt.setName("Test Court");
        testCourt.setBranch(branch);
        testCourt.setSport(sport);
        testCourt.setPricePerHour(100.0);
        testCourt = courtRepository.save(testCourt);
    }

    @Test
    public void testGetByIdResponse_Success() {
        Court court = getCourtByIdUseCase.execute(testCourt.getId());

        assertNotNull(court);
        assertEquals("Test Court", court.name());
    }

    @Test
    public void testGetByIdResponse_NotFound() {
        assertThrows(CourtNotFoundException.class, () -> getCourtByIdUseCase.execute(99999L));
    }

    @Test
    public void testGetAllActiveAndApproved_Success() {
        assertNotNull(getApprovedCourtsUseCase.execute(Pageable.unpaged()));
    }
}
