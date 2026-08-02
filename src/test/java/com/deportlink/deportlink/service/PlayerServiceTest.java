package com.deportlink.deportlink.service;

import com.deportlink.deportlink.application.usecase.player.*;
import com.deportlink.deportlink.domain.model.Player;
import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
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

    @Autowired private RegisterPlayerUseCase registerPlayerUseCase;
    @Autowired private GetPlayerByIdUseCase getPlayerByIdUseCase;
    @Autowired private UpdatePlayerUseCase updatePlayerUseCase;
    @Autowired private DeletePlayerUseCase deletePlayerUseCase;
    @Autowired private PlayerRepository playerRepository;

    private Player testPlayer;

    @BeforeEach
    public void setUp() {
        PlayerRequestDto dto = new PlayerRequestDto();
        dto.setEmail("player@example.com");
        dto.setPassword("password123");
        dto.setFirstName("Test");
        dto.setLastName("Player");
        dto.setPhone("1234567890");
        testPlayer = registerPlayerUseCase.execute(dto);
    }

    @Test
    public void testRegisterPlayer_Success() {
        PlayerRequestDto dto = new PlayerRequestDto();
        dto.setEmail("newplayer@example.com");
        dto.setPassword("password123");
        dto.setFirstName("New");
        dto.setLastName("Player");
        dto.setPhone("9876543210");

        Player player = registerPlayerUseCase.execute(dto);

        assertNotNull(player);
        assertNotNull(player.id());
    }

    @Test
    public void testGetPlayerById_Success() {
        Player retrieved = getPlayerByIdUseCase.execute(testPlayer.id());

        assertNotNull(retrieved);
        assertEquals(testPlayer.id(), retrieved.id());
    }

    @Test
    public void testGetPlayerById_NotFound() {
        assertThrows(PlayerNotFoundException.class, () -> getPlayerByIdUseCase.execute(99999L));
    }

    @Test
    public void testUpdatePlayer_Success() {
        PlayerRequestDto updateDto = new PlayerRequestDto();
        updateDto.setFirstName("Updated");
        updateDto.setLastName("Player");
        updateDto.setPhone("1111111111");
        updateDto.setEmail(testPlayer.email());

        updatePlayerUseCase.execute(testPlayer.id(), updateDto);

        assertEquals("Updated", playerRepository.findById(testPlayer.id()).orElseThrow().getFirstName());
        assertEquals("1111111111", playerRepository.findById(testPlayer.id()).orElseThrow().getPhone());
    }

    @Test
    public void testDeletePlayer_Success() {
        deletePlayerUseCase.execute(testPlayer.id());

        assertTrue(playerRepository.findById(testPlayer.id()).isEmpty());
    }
}
