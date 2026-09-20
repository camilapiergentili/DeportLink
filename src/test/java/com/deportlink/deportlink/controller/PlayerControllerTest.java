package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.player.*;
import com.deportlink.deportlink.domain.model.Player;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import com.deportlink.deportlink.mapper.dto.PlayerMapper;
import com.deportlink.deportlink.security.advice.JwtAccessDeniedHandler;
import com.deportlink.deportlink.security.advice.JwtAuthenticationEntryPoint;
import com.deportlink.deportlink.security.config.PasswordConfig;
import com.deportlink.deportlink.security.config.SecurityConfig;
import com.deportlink.deportlink.security.service.JwtFilter;
import com.deportlink.deportlink.security.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre únicamente POST /api/players (registro, endpoint público) — verifica que
 * password/confirmPassword distintos se traduzcan a 400 y que RegisterPlayerUseCase nunca se
 * invoque en ese caso (la contraseña no se procesa ni se guarda).
 */
@WebMvcTest(controllers = PlayerController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, PasswordConfig.class})
@ActiveProfiles("test")
class PlayerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean RegisterPlayerUseCase registerPlayerUseCase;
    @MockBean GetPlayerByIdUseCase getPlayerByIdUseCase;
    @MockBean UpdatePlayerUseCase updatePlayerUseCase;
    @MockBean DeletePlayerUseCase deletePlayerUseCase;
    @MockBean PlayerMapper playerMapper;

    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtFilter jwtFilter;

    @BeforeEach
    void setup() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2))
                    .doFilter((ServletRequest) inv.getArgument(0), (ServletResponse) inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    private static final String VALID_ADDRESS = """
            "addressRequestDto": {
                "streetName": "Av. Corrientes",
                "number": 1234,
                "city": "CABA",
                "province": "Buenos Aires",
                "postalCode": 1043,
                "latitude": -34.6,
                "longitude": -58.4
            }
            """;

    private String registerBody(String password, String confirmPassword) {
        return """
                {
                    "firstName": "New",
                    "lastName": "Player",
                    "email": "newplayer@example.com",
                    "password": "%s",
                    "confirmPassword": "%s",
                    "phone": "1234567890",
                    %s
                }
                """.formatted(password, confirmPassword, VALID_ADDRESS);
    }

    @Test
    void register_passwordsDistintas_retorna400YNoRegistraAlJugador() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType("application/json")
                        .content(registerBody("password123", "otraPassword")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(registerPlayerUseCase);
    }

    @Test
    void register_passwordSuperaLimiteDeBytesDelEncoder_retorna400YNoRegistraAlJugador() throws Exception {
        // 73 bytes ASCII: supera el límite de 72 bytes que BCrypt acepta (ver N1,
        // docs/software-review-2026-09-11.md). Antes del fix, esto llegaba a
        // passwordEncoder.encode() y explotaba con IllegalArgumentException -> 500.
        String tooLongPassword = "a".repeat(73);

        mockMvc.perform(post("/api/players")
                        .contentType("application/json")
                        .content(registerBody(tooLongPassword, tooLongPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(registerPlayerUseCase);
    }

    @Test
    void register_passwordsIguales_registraAlJugador() throws Exception {
        Player saved = new Player(1L, "New", "Player", "newplayer@example.com",
                "1234567890", "encoded-irrelevant", Set.of());
        when(registerPlayerUseCase.execute(any())).thenReturn(saved);
        when(playerMapper.toResponse(saved)).thenReturn(new PlayerResponseDto());

        mockMvc.perform(post("/api/players")
                        .contentType("application/json")
                        .content(registerBody("password123", "password123")))
                .andExpect(status().isCreated());

        verify(registerPlayerUseCase).execute(any());
    }
}
