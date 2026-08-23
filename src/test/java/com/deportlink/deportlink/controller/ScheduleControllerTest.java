package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.schedule.*;
import com.deportlink.deportlink.exception.ScheduleHasReservationsException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import com.deportlink.deportlink.mapper.dto.ScheduleMapper;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre únicamente DELETE /api/schedules/{idSchedule}/court/{idCourt} — verifica que
 * ScheduleHasReservationsException y DataIntegrityViolationException se traduzcan a 409
 * (auditoría, Paso 2) y que los handlers existentes (404, 401) sigan funcionando. El resto de
 * ScheduleController no cambió en este paso y no se retestea acá.
 */
@WebMvcTest(controllers = ScheduleController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, PasswordConfig.class})
@ActiveProfiles("test")
class ScheduleControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean AddScheduleUseCase addScheduleUseCase;
    @MockBean DeleteScheduleUseCase deleteScheduleUseCase;
    @MockBean UpdateScheduleUseCase updateScheduleUseCase;
    @MockBean GetAllSchedulesByCourtUseCase getAllSchedulesByCourtUseCase;
    @MockBean GetScheduleByDayUseCase getScheduleByDayUseCase;
    @MockBean ScheduleMapper scheduleMapper;

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

    @Test
    void delete_horarioSinReservas_retorna200() throws Exception {
        mockMvc.perform(delete("/api/schedules/1/court/10").with(ADMIN))
                .andExpect(status().isOk());

        verify(deleteScheduleUseCase).execute(1L, 10L);
    }

    @Test
    void delete_horarioConReservas_retorna409() throws Exception {
        doThrow(new ScheduleHasReservationsException("No se puede eliminar, ya que existen reservas para ese dia"))
                .when(deleteScheduleUseCase).execute(1L, 10L);

        mockMvc.perform(delete("/api/schedules/1/court/10").with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void delete_horarioNoExiste_retorna404() throws Exception {
        doThrow(new ScheduleNotFoundException("No se encontró la agenda"))
                .when(deleteScheduleUseCase).execute(99L, 10L);

        mockMvc.perform(delete("/api/schedules/99/court/10").with(ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void delete_conflictoDeIntegridadEnCarrera_retorna409() throws Exception {
        // Simula una reserva insertada justo entre el chequeo de negocio y el delete real.
        doThrow(new DataIntegrityViolationException("constraint violation"))
                .when(deleteScheduleUseCase).execute(1L, 10L);

        mockMvc.perform(delete("/api/schedules/1/court/10").with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void delete_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(delete("/api/schedules/1/court/10"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(deleteScheduleUseCase);
    }
}
