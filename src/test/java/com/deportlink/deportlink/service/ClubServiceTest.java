package com.deportlink.deportlink.service;

import com.deportlink.deportlink.application.usecase.club.*;
import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.port.out.PageRequest;
import com.deportlink.deportlink.enums.ClubType;
import com.deportlink.deportlink.exception.ClubAlreadyExistsException;
import com.deportlink.deportlink.model.entity.OwnerEntity;
import com.deportlink.deportlink.persistence.repository.ClubRepository;
import com.deportlink.deportlink.persistence.repository.OwnerRepository;
import com.deportlink.deportlink.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ClubServiceTest {

    @Autowired private CreateClubUseCase createClubUseCase;
    @Autowired private DeleteClubUseCase deleteClubUseCase;
    @Autowired private GetAllClubsUseCase getAllClubsUseCase;
    @Autowired private ClubRepository clubRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private UserRepository userRepository;

    private OwnerEntity testOwner;

    @BeforeEach
    public void setUp() {
        testOwner = new OwnerEntity();
        testOwner.setEmail("owner@example.com");
        testOwner.setPassword("password123");
        testOwner.setFirstName("Test");
        testOwner.setLastName("Owner");
        testOwner.setPhone("1234567890");
        testOwner.setDni(20123456789L);
        testOwner.setCuil("20123456789");
        testOwner.setDateOfBirth(LocalDate.of(1985, 1, 1));
        testOwner = ownerRepository.save(testOwner);
    }

    @Test
    public void testCreateClub_Success() {
        Club club = createClubUseCase.execute(
                "New Club", "New Club Legal", "30987654321",
                ClubType.SA, Set.of(testOwner.getId()));
        assertNotNull(club);
        assertNotNull(club.id());
        assertEquals("New Club", club.name());
    }

    @Test
    public void testCreateClub_DuplicateCuit() {
        createClubUseCase.execute("Club 1", "Club 1 Legal", "30111111111",
                ClubType.SA, Set.of(testOwner.getId()));
        assertThrows(ClubAlreadyExistsException.class, () ->
                createClubUseCase.execute("Club 2", "Club 2 Legal", "30111111111",
                        ClubType.SA, Set.of(testOwner.getId())));
    }

    @Test
    public void testCreateClub_DuplicateLegalName() {
        createClubUseCase.execute("Club 1", "Unique Legal Name", "30111111111",
                ClubType.SA, Set.of(testOwner.getId()));
        assertThrows(ClubAlreadyExistsException.class, () ->
                createClubUseCase.execute("Club 2", "Unique Legal Name", "30222222222",
                        ClubType.SA, Set.of(testOwner.getId())));
    }

    @Test
    public void testDeleteClub_Success() {
        Club created = createClubUseCase.execute("Club to Delete", "Club Legal", "30333333333",
                ClubType.SA, Set.of(testOwner.getId()));
        deleteClubUseCase.execute(created.id());
        assertFalse(clubRepository.findById(created.id()).isPresent());
    }

    @Test
    public void testGetAll_Success() {
        createClubUseCase.execute("Club 1", "Club 1 Legal", "30444444444",
                ClubType.SA, Set.of(testOwner.getId()));
        createClubUseCase.execute("Club 2", "Club 2 Legal", "30555555555",
                ClubType.SRL, Set.of(testOwner.getId()));
        assertTrue(getAllClubsUseCase.execute(PageRequest.unpaged()).totalElements() >= 2);
    }

    @Test
    public void testGetById_AfterCreate_Success() {
        Club created = createClubUseCase.execute("Test Club", "Test Legal", "30666666666",
                ClubType.SA, Set.of(testOwner.getId()));
        assertTrue(clubRepository.findById(created.id()).isPresent());
        assertEquals("Test Club", clubRepository.findById(created.id()).get().getName());
    }
}
