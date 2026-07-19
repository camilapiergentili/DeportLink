package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.persistence.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;


import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PlayerServiceTest {

    @Autowired
    private PlayerService playerService;

    @Autowired
    private PlayerRepository playerRepository;

    private PlayerEntity testUser;

    @BeforeEach
    public void setUp() {
        // Create test player
        testUser = new PlayerEntity();
        testUser.setEmail("player@example.com");
        testUser.setPassword("password123");
        testUser.setFirstName("Test");
        testUser.setLastName("Player");
        testUser.setPhone("1234567890");
        testUser = playerRepository.save(testUser);
    }

    @Test
    public void testRegisterPlayer_Success() {
        PlayerRequestDto dto = new PlayerRequestDto();
        dto.setEmail("newplayer@example.com");
        dto.setPassword("password123");
        dto.setFirstName("New");
        dto.setLastName("Player");
        dto.setPhone("9876543210");

        PlayerResponseDto response = playerService.register(dto);

        assertNotNull(response);
        assertNotNull(response.getId());
    }

    @Test
    public void testGetPlayerById_Success() {
        // Get by ID
        PlayerEntity retrieved = playerService.getById(testUser.getId());

        assertNotNull(retrieved);
        assertEquals(testUser.getId(), retrieved.getId());
    }

    @Test
    public void testGetPlayerById_NotFound() {
        assertThrows(PlayerNotFoundException.class, () -> playerService.getById(99999L));
    }

    @Test
    public void testUpdatePlayer_Success() {
        // Update
        PlayerRequestDto updateDto = new PlayerRequestDto();
        updateDto.setFirstName("Updated");
        updateDto.setLastName("Player");
        updateDto.setPhone("1111111111");
        updateDto.setEmail(testUser.getEmail());

        playerService.update(testUser.getId(), updateDto);

        // Verify
        PlayerEntity updated = playerRepository.findById(testUser.getId()).orElseThrow();
        assertEquals("Updated", updated.getFirstName());
        assertEquals("1111111111", updated.getPhone());
    }

    @Test
    public void testDeletePlayer_Success() {
        // Delete
        playerService.delete(testUser.getId());

        // Verify
        assertTrue(playerRepository.findById(testUser.getId()).isEmpty());
    }
}
