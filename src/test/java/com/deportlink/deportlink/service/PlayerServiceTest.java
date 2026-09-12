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
        // P0.1: el Player no se borra físicamente — se anonimiza, porque Reservation/Ticket
        // dependen de su id y deben sobrevivir. Ver PlayerRepositoryAdapter.delete().
        String originalEmail = testPlayer.email();
        String originalPassword = playerRepository.findById(testPlayer.id()).orElseThrow().getPassword();

        deletePlayerUseCase.execute(testPlayer.id());

        var afterwards = playerRepository.findById(testPlayer.id());

        // El Player sigue existiendo físicamente — no se elimina la fila.
        assertTrue(afterwards.isPresent(), "El Player debe seguir existiendo físicamente (anonimizado, no eliminado)");

        // El email original deja de estar asociado a ningún Player — no sirve para login.
        assertNotEquals(originalEmail, afterwards.get().getEmail());
        assertTrue(playerRepository.findByEmail(originalEmail).isEmpty(),
                "El email original no debe resolver a ningún usuario después de la anonimización");

        // Los campos identificables quedaron anonimizados según la implementación actual.
        assertEquals("Usuario", afterwards.get().getFirstName());
        assertEquals("eliminado", afterwards.get().getLastName());
        assertNull(afterwards.get().getPhone());
        assertNotEquals(originalPassword, afterwards.get().getPassword(),
                "La contraseña debe reemplazarse por una aleatoria e inutilizable");
    }
}
