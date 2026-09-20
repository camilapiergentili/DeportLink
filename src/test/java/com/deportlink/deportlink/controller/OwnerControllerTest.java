package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.owner.*;
import com.deportlink.deportlink.domain.model.Owner;
import com.deportlink.deportlink.dto.response.OwnerResponseDto;
import com.deportlink.deportlink.mapper.dto.OwnerMapper;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre únicamente POST /api/owners (registro, rol ADMIN) — verifica que
 * password/confirmPassword distintos se traduzcan a 400 y que RegisterOwnerUseCase nunca se
 * invoque en ese caso (la contraseña no se procesa ni se guarda).
 */
@WebMvcTest(controllers = OwnerController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, PasswordConfig.class})
@ActiveProfiles("test")
class OwnerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean RegisterOwnerUseCase registerOwnerUseCase;
    @MockBean GetOwnerByIdUseCase getOwnerByIdUseCase;
    @MockBean GetAllOwnersUseCase getAllOwnersUseCase;
    @MockBean UpdateOwnerUseCase updateOwnerUseCase;
    @MockBean DeleteOwnerUseCase deleteOwnerUseCase;
    @MockBean OwnerMapper ownerMapper;

    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtFilter jwtFilter;

    private static final RequestPostProcessor ADMIN = user("admin@test.com").roles("ADMIN");

    @BeforeEach
    void setup() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2))
                    .doFilter((ServletRequest) inv.getArgument(0), (ServletResponse) inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    private String registerBody(String password, String confirmPassword) {
        return """
                {
                    "firstName": "Test",
                    "lastName": "Owner",
                    "email": "owner@example.com",
                    "password": "%s",
                    "confirmPassword": "%s",
                    "phone": "1234567890",
                    "dni": 20123456789,
                    "cuil": "20123456789",
                    "dateOfBirth": "01/01/1985"
                }
                """.formatted(password, confirmPassword);
    }

    @Test
    void register_passwordsDistintas_retorna400YNoRegistraAlDueño() throws Exception {
        mockMvc.perform(post("/api/owners").with(ADMIN)
                        .contentType("application/json")
                        .content(registerBody("password123", "otraPassword")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(registerOwnerUseCase);
    }

    @Test
    void register_passwordsIguales_registraAlDueño() throws Exception {
        Owner saved = new Owner(1L, "Test", "Owner", "owner@example.com", "1234567890",
                20123456789L, "20123456789", LocalDate.of(1985, 1, 1), "encoded-irrelevant");
        when(registerOwnerUseCase.execute(any())).thenReturn(saved);
        when(ownerMapper.toResponse(saved)).thenReturn(new OwnerResponseDto());

        mockMvc.perform(post("/api/owners").with(ADMIN)
                        .contentType("application/json")
                        .content(registerBody("password123", "password123")))
                .andExpect(status().isCreated());

        verify(registerOwnerUseCase).execute(any());
    }
}
