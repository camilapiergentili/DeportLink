package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.ClubRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.exception.ClubAlreadyExistsException;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import com.deportlink.deportlink.service.ClubOwnerService;
import com.deportlink.deportlink.enums.ClubType;
import com.deportlink.deportlink.model.entity.ClubEntity;
import com.deportlink.deportlink.model.entity.OwnerEntity;
import com.deportlink.deportlink.model.entity.UserEntity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ClubServiceTest {

    @Autowired
    private ClubService clubService;

    @Autowired
    private ClubOwnerService clubOwnerService;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private UserRepository userRepository;

    private OwnerEntity testOwner;

    @BeforeEach
    public void setUp() {
        // Create test owner
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
        ClubRequestDto dto = new ClubRequestDto();
        dto.setName("New Club");
        dto.setLegalName("New Club Legal");
        dto.setCuit("30987654321");
        dto.setClubType(ClubType.SA);
        dto.setOwnerIds(Set.of(testOwner.getId()));

        ClubResponseDto response = clubOwnerService.create(dto);

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals("New Club", response.getName());
    }

    @Test
    public void testCreateClub_DuplicateCuit() {
        // Create first club
        ClubRequestDto dto1 = new ClubRequestDto();
        dto1.setName("Club 1");
        dto1.setLegalName("Club 1 Legal");
        dto1.setCuit("30111111111");
        dto1.setClubType(ClubType.SA);
        dto1.setOwnerIds(Set.of(testOwner.getId()));
        clubOwnerService.create(dto1);

        // Try to create club with same CUIT
        ClubRequestDto dto2 = new ClubRequestDto();
        dto2.setName("Club 2");
        dto2.setLegalName("Club 2 Legal");
        dto2.setCuit("30111111111");
        dto2.setClubType(ClubType.SA);
        dto2.setOwnerIds(Set.of(testOwner.getId()));

        assertThrows(ClubAlreadyExistsException.class, () -> clubOwnerService.create(dto2));
    }

    @Test
    public void testCreateClub_DuplicateLegalName() {
        // Create first club
        ClubRequestDto dto1 = new ClubRequestDto();
        dto1.setName("Club 1");
        dto1.setLegalName("Unique Legal Name");
        dto1.setCuit("30111111111");
        dto1.setClubType(ClubType.SA);
        dto1.setOwnerIds(Set.of(testOwner.getId()));
        clubOwnerService.create(dto1);

        // Try to create club with same legal name
        ClubRequestDto dto2 = new ClubRequestDto();
        dto2.setName("Club 2");
        dto2.setLegalName("Unique Legal Name");
        dto2.setCuit("30222222222");
        dto2.setClubType(ClubType.SA);
        dto2.setOwnerIds(Set.of(testOwner.getId()));

        assertThrows(ClubAlreadyExistsException.class, () -> clubOwnerService.create(dto2));
    }

    @Test
    public void testDeleteClub_Success() {
        // Create club
        ClubRequestDto createDto = new ClubRequestDto();
        createDto.setName("Club to Delete");
        createDto.setLegalName("Club Legal");
        createDto.setCuit("30333333333");
        createDto.setClubType(ClubType.SA);
        createDto.setOwnerIds(Set.of(testOwner.getId()));
        ClubResponseDto created = clubOwnerService.create(createDto);

        // Delete club
        clubOwnerService.delete(created.getId());

        // Verify
        assertThrows(ClubNotFoundException.class, () -> clubService.getById(created.getId()));
    }

    @Test
    public void testGetAll_Success() {
        // Create multiple clubs
        ClubRequestDto dto1 = new ClubRequestDto();
        dto1.setName("Club 1");
        dto1.setLegalName("Club 1 Legal");
        dto1.setCuit("30444444444");
        dto1.setClubType(ClubType.SA);
        dto1.setOwnerIds(Set.of(testOwner.getId()));
        clubOwnerService.create(dto1);

        ClubRequestDto dto2 = new ClubRequestDto();
        dto2.setName("Club 2");
        dto2.setLegalName("Club 2 Legal");
        dto2.setCuit("30555555555");
        dto2.setClubType(ClubType.SRL);
        dto2.setOwnerIds(Set.of(testOwner.getId()));
        clubOwnerService.create(dto2);

        // Get all
        List<ClubResponseDto> clubs = clubService.getAll();
        assertNotNull(clubs);
        assertTrue(clubs.size() >= 2);
    }

    @Test
    public void testGetById_AfterCreate_Success() {
        ClubRequestDto createDto = new ClubRequestDto();
        createDto.setName("Test Club");
        createDto.setLegalName("Test Legal");
        createDto.setCuit("30666666666");
        createDto.setClubType(ClubType.SA);
        createDto.setOwnerIds(Set.of(testOwner.getId()));
        ClubResponseDto created = clubOwnerService.create(createDto);

        ClubEntity found = clubService.getById(created.getId());
        assertNotNull(found);
        assertEquals("Test Club", found.getName());
    }
}
