package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.branch.*;
import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.StatusAlreadyAppliedException;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {BranchController.class, BranchAdminController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, PasswordConfig.class})
@ActiveProfiles("test")
class BranchControllerTest {

    @Autowired
    MockMvc mockMvc;

    // ─── Use cases ───────────────────────────────────────────────────────────────
    @MockBean GetBranchUseCase getBranchUseCase;
    @MockBean GetApprovedBranchesUseCase getApprovedBranchesUseCase;
    @MockBean SearchBranchesByNameUseCase searchBranchesByNameUseCase;
    @MockBean GetBranchesBySportUseCase getBranchesBySportUseCase;
    @MockBean GetNearbyBranchesUseCase getNearbyBranchesUseCase;
    @MockBean GetBranchByIdUseCase getBranchByIdUseCase;
    @MockBean GetAllBranchesUseCase getAllBranchesUseCase;
    @MockBean ApproveBranchUseCase approveBranchUseCase;
    @MockBean RejectBranchUseCase rejectBranchUseCase;

    // ─── Security infrastructure ─────────────────────────────────────────────────
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtFilter jwtFilter;

    // ─── Auth helpers ────────────────────────────────────────────────────────────
    private static final RequestPostProcessor PLAYER = user("player@test.com").roles("PLAYER");
    private static final RequestPostProcessor ADMIN  = user("admin@test.com").roles("ADMIN");

    @BeforeEach
    void setup() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2))
                    .doFilter((ServletRequest) inv.getArgument(0), (ServletResponse) inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    private static Branch branch() {
        Address addr = new Address("Av. Corrientes", 1234, "CABA", "Buenos Aires", 1043, -34.6, -58.4);
        return new Branch(1L, "Norte", addr, 10L, VerificationStatus.APPROVED, ActiveStatus.ACTIVE);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BranchController — GET /{idBranch}/approved
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void getApprovedById_autenticado_retorna200ConDatos() throws Exception {
        when(getBranchUseCase.execute(1L)).thenReturn(branch());

        mockMvc.perform(get("/api/branches/1/approved").with(PLAYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Norte"))
                .andExpect(jsonPath("$.clubId").value(10))
                .andExpect(jsonPath("$.address.city").value("CABA"));

        verify(getBranchUseCase).execute(1L);
    }

    @Test
    void getApprovedById_sucursalNoExiste_retorna404() throws Exception {
        when(getBranchUseCase.execute(99L)).thenThrow(new BranchNotFoundException("Sucursal no encontrada"));

        mockMvc.perform(get("/api/branches/99/approved").with(PLAYER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void getApprovedById_sucursalNoAprobada_retorna403() throws Exception {
        when(getBranchUseCase.execute(1L))
                .thenThrow(new BranchNotApprovedException("La sucursal no se puede mostrar"));

        mockMvc.perform(get("/api/branches/1/approved").with(PLAYER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void getApprovedById_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(get("/api/branches/1/approved"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getBranchUseCase);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BranchController — GET /{idClub}/active-approved
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void getApprovedAndActiveByClub_conResultados_retorna200() throws Exception {
        when(getApprovedBranchesUseCase.execute(10L)).thenReturn(List.of(branch()));

        mockMvc.perform(get("/api/branches/10/active-approved").with(PLAYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Norte"));

        verify(getApprovedBranchesUseCase).execute(10L);
    }

    @Test
    void getApprovedAndActiveByClub_listaVacia_retorna204() throws Exception {
        when(getApprovedBranchesUseCase.execute(10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/branches/10/active-approved").with(PLAYER))
                .andExpect(status().isNoContent());
    }

    @Test
    void getApprovedAndActiveByClub_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(get("/api/branches/10/active-approved"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getApprovedBranchesUseCase);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BranchController — GET /search?name=
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void search_conResultados_retorna200() throws Exception {
        when(searchBranchesByNameUseCase.execute("norte")).thenReturn(List.of(branch()));

        mockMvc.perform(get("/api/branches/search").param("name", "norte").with(PLAYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Norte"));
    }

    @Test
    void search_sinResultados_retorna204() throws Exception {
        when(searchBranchesByNameUseCase.execute("xyz")).thenReturn(List.of());

        mockMvc.perform(get("/api/branches/search").param("name", "xyz").with(PLAYER))
                .andExpect(status().isNoContent());
    }

    @Test
    void search_parametroNameAusente_retorna400() throws Exception {
        mockMvc.perform(get("/api/branches/search").with(PLAYER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchBranchesByNameUseCase);
    }

    @Test
    void search_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(get("/api/branches/search").param("name", "norte"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(searchBranchesByNameUseCase);
    }

    @Test
    void search_delegaNameExactoAlUseCase() throws Exception {
        when(searchBranchesByNameUseCase.execute("norte")).thenReturn(List.of());

        mockMvc.perform(get("/api/branches/search").param("name", "norte").with(PLAYER));

        verify(searchBranchesByNameUseCase).execute("norte");
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BranchController — GET /by-sport/{sportId}
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void getBySport_conResultados_retorna200() throws Exception {
        when(getBranchesBySportUseCase.execute(3L)).thenReturn(List.of(branch()));

        mockMvc.perform(get("/api/branches/by-sport/3").with(PLAYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Norte"));

        verify(getBranchesBySportUseCase).execute(3L);
    }

    @Test
    void getBySport_listaVacia_retorna204() throws Exception {
        when(getBranchesBySportUseCase.execute(3L)).thenReturn(List.of());

        mockMvc.perform(get("/api/branches/by-sport/3").with(PLAYER))
                .andExpect(status().isNoContent());
    }

    @Test
    void getBySport_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(get("/api/branches/by-sport/3"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getBranchesBySportUseCase);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BranchController — GET /nearby
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void getNearby_conResultados_retorna200() throws Exception {
        when(getNearbyBranchesUseCase.execute(-34.6, -58.4, 5.0)).thenReturn(List.of(branch()));

        mockMvc.perform(get("/api/branches/nearby")
                        .param("lat", "-34.6")
                        .param("lng", "-58.4")
                        .param("radiusKm", "5.0")
                        .with(PLAYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Norte"));
    }

    @Test
    void getNearby_listaVacia_retorna204() throws Exception {
        when(getNearbyBranchesUseCase.execute(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());

        mockMvc.perform(get("/api/branches/nearby")
                        .param("lat", "-34.6")
                        .param("lng", "-58.4")
                        .with(PLAYER))
                .andExpect(status().isNoContent());
    }

    @Test
    void getNearby_sinParametroLat_retorna400() throws Exception {
        mockMvc.perform(get("/api/branches/nearby")
                        .param("lng", "-58.4")
                        .with(PLAYER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getNearbyBranchesUseCase);
    }

    @Test
    void getNearby_sinParametroLng_retorna400() throws Exception {
        mockMvc.perform(get("/api/branches/nearby")
                        .param("lat", "-34.6")
                        .with(PLAYER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getNearbyBranchesUseCase);
    }

    @Test
    void getNearby_radiusKmPorDefectoEs10() throws Exception {
        when(getNearbyBranchesUseCase.execute(-34.6, -58.4, 10.0)).thenReturn(List.of());

        mockMvc.perform(get("/api/branches/nearby")
                        .param("lat", "-34.6")
                        .param("lng", "-58.4")
                        .with(PLAYER));

        verify(getNearbyBranchesUseCase).execute(-34.6, -58.4, 10.0);
    }

    @Test
    void getNearby_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(get("/api/branches/nearby")
                        .param("lat", "-34.6")
                        .param("lng", "-58.4"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getNearbyBranchesUseCase);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BranchAdminController — GET /{id}
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void admin_getById_rolAdmin_retorna200ConDatos() throws Exception {
        when(getBranchByIdUseCase.execute(1L)).thenReturn(branch());

        mockMvc.perform(get("/api/branches/1").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Norte"));

        verify(getBranchByIdUseCase).execute(1L);
    }

    @Test
    void admin_getById_sucursalNoExiste_retorna404() throws Exception {
        when(getBranchByIdUseCase.execute(99L)).thenThrow(new BranchNotFoundException("Sucursal no encontrada"));

        mockMvc.perform(get("/api/branches/99").with(ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void admin_getById_rolPlayer_retorna403() throws Exception {
        mockMvc.perform(get("/api/branches/1").with(PLAYER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(getBranchByIdUseCase);
    }

    @Test
    void admin_getById_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(get("/api/branches/1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getBranchByIdUseCase);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BranchAdminController — GET /{idClub}/club
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void admin_getAll_conResultados_retorna200() throws Exception {
        when(getAllBranchesUseCase.execute(10L)).thenReturn(List.of(branch()));

        mockMvc.perform(get("/api/branches/10/club").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Norte"));

        verify(getAllBranchesUseCase).execute(10L);
    }

    @Test
    void admin_getAll_listaVacia_retorna204() throws Exception {
        when(getAllBranchesUseCase.execute(10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/branches/10/club").with(ADMIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void admin_getAll_rolPlayer_retorna403() throws Exception {
        mockMvc.perform(get("/api/branches/10/club").with(PLAYER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(getAllBranchesUseCase);
    }

    @Test
    void admin_getAll_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(get("/api/branches/10/club"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getAllBranchesUseCase);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BranchAdminController — PATCH /{id}/approve
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void admin_approve_rolAdmin_retorna200ConMensaje() throws Exception {
        mockMvc.perform(patch("/api/branches/1/approve").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sucursal aprobada con éxito"));

        verify(approveBranchUseCase).execute(1L);
    }

    @Test
    void admin_approve_sucursalNoExiste_retorna404() throws Exception {
        when(approveBranchUseCase.execute(99L)).thenThrow(new BranchNotFoundException("Sucursal no encontrada"));

        mockMvc.perform(patch("/api/branches/99/approve").with(ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void admin_approve_estadoYaAprobado_retorna409() throws Exception {
        when(approveBranchUseCase.execute(1L))
                .thenThrow(new StatusAlreadyAppliedException("La sucursal ya está aprobada"));

        mockMvc.perform(patch("/api/branches/1/approve").with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void admin_approve_rolPlayer_retorna403() throws Exception {
        mockMvc.perform(patch("/api/branches/1/approve").with(PLAYER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(approveBranchUseCase);
    }

    @Test
    void admin_approve_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(patch("/api/branches/1/approve"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(approveBranchUseCase);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BranchAdminController — PATCH /{id}/reject
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void admin_reject_rolAdmin_retorna200ConMensaje() throws Exception {
        mockMvc.perform(patch("/api/branches/1/reject").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sucursal rechazada con éxito"));

        verify(rejectBranchUseCase).execute(1L);
    }

    @Test
    void admin_reject_sucursalNoExiste_retorna404() throws Exception {
        when(rejectBranchUseCase.execute(99L)).thenThrow(new BranchNotFoundException("Sucursal no encontrada"));

        mockMvc.perform(patch("/api/branches/99/reject").with(ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void admin_reject_estadoYaRechazado_retorna409() throws Exception {
        when(rejectBranchUseCase.execute(1L))
                .thenThrow(new StatusAlreadyAppliedException("La sucursal ya está rechazada"));

        mockMvc.perform(patch("/api/branches/1/reject").with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void admin_reject_rolPlayer_retorna403() throws Exception {
        mockMvc.perform(patch("/api/branches/1/reject").with(PLAYER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(rejectBranchUseCase);
    }

    @Test
    void admin_reject_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(patch("/api/branches/1/reject"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(rejectBranchUseCase);
    }
}
