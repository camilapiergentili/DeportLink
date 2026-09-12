package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.court.*;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.exception.BranchNotEligibleException;
import com.deportlink.deportlink.mapper.dto.CourtMapper;
import com.deportlink.deportlink.model.Rol;
import com.deportlink.deportlink.model.entity.UserEntity;
import com.deportlink.deportlink.model.entity.UserMain;
import com.deportlink.deportlink.persistence.repository.BranchRepository;
import com.deportlink.deportlink.persistence.repository.CourtRepository;
import com.deportlink.deportlink.security.advice.JwtAccessDeniedHandler;
import com.deportlink.deportlink.security.advice.JwtAuthenticationEntryPoint;
import com.deportlink.deportlink.security.authorization.BranchAuthorization;
import com.deportlink.deportlink.security.authorization.CourtAuthorization;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre PATCH /api/courts/owner/{idCourt}/branch (mover cancha de sucursal, nuevo endpoint) y
 * la corrección del @PreAuthorize de PUT /api/courts/owner/{idCourt} (ya no exige ser dueño de
 * la sucursal, porque update() no mueve la cancha). El resto de CourtOwnerController no cambió
 * en este paso y no se retestea acá.
 * <p>
 * CourtAuthorization/BranchAuthorization se importan REALES (no @MockBean) porque el
 * @PreAuthorize las resuelve por nombre de bean vía SpEL (@courtAuthorization,
 * @branchAuthorization) — un @MockBean del propio componente no queda registrado bajo ese
 * nombre en un @WebMvcTest. En cambio, mockeamos los JPA repository que ellas usan por debajo.
 */
@WebMvcTest(controllers = CourtOwnerController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, PasswordConfig.class,
        CourtAuthorization.class, BranchAuthorization.class})
@ActiveProfiles("test")
class CourtOwnerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean CreateCourtUseCase createCourtUseCase;
    @MockBean UpdateCourtUseCase updateCourtUseCase;
    @MockBean UpdateCourtPriceUseCase updateCourtPriceUseCase;
    @MockBean DeleteCourtUseCase deleteCourtUseCase;
    @MockBean ActivateCourtUseCase activateCourtUseCase;
    @MockBean DeactivateCourtUseCase deactivateCourtUseCase;
    @MockBean MoveCourtToBranchUseCase moveCourtToBranchUseCase;
    @MockBean CourtMapper courtMapper;

    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtFilter jwtFilter;

    @MockBean CourtRepository courtRepository;
    @MockBean BranchRepository branchRepository;

    private static final RequestPostProcessor ADMIN = user("admin@test.com").roles("ADMIN");

    // CourtAuthorization/BranchAuthorization exigen un principal UserMain (leen user.getId()).
    // user(...).roles("OWNER") arma un org.springframework.security.core.userdetails.User
    // genérico, no un UserMain — con eso el "instanceof UserMain" de esas clases da false y
    // el chequeo de ownership falla siempre. Hay que autenticar con un UserMain real.
    private static RequestPostProcessor owner(long id) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setEmail("owner" + id + "@test.com");
        entity.setPassword("irrelevant");
        entity.setRole(Rol.OWNER);
        UserMain principal = new UserMain(entity);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        return authentication(auth);
    }

    @BeforeEach
    void setup() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2))
                    .doFilter((ServletRequest) inv.getArgument(0), (ServletResponse) inv.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    private static Court court() {
        return new Court(1L, "Cancha 1", 100.0, 20L, 5L, "Football", ActiveStatus.ACTIVE);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // PATCH /{idCourt}/branch — nuevo endpoint
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void moveToBranch_ownerDeCanchaYDeSucursalDestino_retorna200() throws Exception {
        when(courtRepository.existsByCourtAndOwner(anyLong(), anyLong())).thenReturn(true);
        when(branchRepository.existsByIdAndClub_Owners_Id(anyLong(), anyLong())).thenReturn(true);
        when(moveCourtToBranchUseCase.execute(1L, 20L)).thenReturn(court());
        when(courtMapper.toResponse(any())).thenReturn(new CourtResponseDto());

        mockMvc.perform(patch("/api/courts/owner/1/branch").with(owner(1L))
                        .contentType("application/json")
                        .content("{\"newBranchId\": 20}"))
                .andExpect(status().isOk());

        verify(moveCourtToBranchUseCase).execute(1L, 20L);
    }

    @Test
    void moveToBranch_ownerDeCanchaPeroNoDeSucursalDestino_retorna403() throws Exception {
        when(courtRepository.existsByCourtAndOwner(anyLong(), anyLong())).thenReturn(true);
        when(branchRepository.existsByIdAndClub_Owners_Id(anyLong(), anyLong())).thenReturn(false);

        mockMvc.perform(patch("/api/courts/owner/1/branch").with(owner(1L))
                        .contentType("application/json")
                        .content("{\"newBranchId\": 20}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(moveCourtToBranchUseCase);
    }

    @Test
    void moveToBranch_noOwnerDeLaCancha_retorna403() throws Exception {
        when(courtRepository.existsByCourtAndOwner(anyLong(), anyLong())).thenReturn(false);

        mockMvc.perform(patch("/api/courts/owner/1/branch").with(owner(1L))
                        .contentType("application/json")
                        .content("{\"newBranchId\": 20}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(moveCourtToBranchUseCase);
    }

    @Test
    void moveToBranch_admin_retorna200SinConsultarOwnership() throws Exception {
        when(moveCourtToBranchUseCase.execute(1L, 20L)).thenReturn(court());
        when(courtMapper.toResponse(any())).thenReturn(new CourtResponseDto());

        mockMvc.perform(patch("/api/courts/owner/1/branch").with(ADMIN)
                        .contentType("application/json")
                        .content("{\"newBranchId\": 20}"))
                .andExpect(status().isOk());

        // hasRole('ADMIN') corta el OR por cortocircuito — nunca debería resolver ownership.
        verifyNoInteractions(courtRepository, branchRepository);
    }

    @Test
    void moveToBranch_admin_sucursalDestinoNoElegible_retorna403() throws Exception {
        // La regla "sucursal APPROVED+ACTIVE" vive en el use case, no en el @PreAuthorize —
        // por eso alcanza también a ADMIN.
        when(moveCourtToBranchUseCase.execute(1L, 20L))
                .thenThrow(new BranchNotEligibleException(
                        "La sucursal destino debe estar aprobada y activa para poder mover la cancha"));

        mockMvc.perform(patch("/api/courts/owner/1/branch").with(ADMIN)
                        .contentType("application/json")
                        .content("{\"newBranchId\": 20}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void moveToBranch_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(patch("/api/courts/owner/1/branch")
                        .contentType("application/json")
                        .content("{\"newBranchId\": 20}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(moveCourtToBranchUseCase);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // PUT /{idCourt} — ya no debe exigir ownership de la sucursal (fix de la validación
    // redundante: update() nunca mueve la cancha de sucursal)
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void update_ownerDeCanchaPeroNoDeSucursal_retorna200() throws Exception {
        when(courtRepository.existsByCourtAndOwner(anyLong(), anyLong())).thenReturn(true);
        when(updateCourtUseCase.execute(eq(1L), any(), any())).thenReturn(court());
        when(courtMapper.toResponse(any())).thenReturn(new CourtResponseDto());

        mockMvc.perform(put("/api/courts/owner/1").with(owner(1L))
                        .contentType("application/json")
                        .content("{\"name\": \"Cancha renombrada\", \"pricePerHour\": 100, \"idBranch\": 20, \"idSport\": 5}"))
                .andExpect(status().isOk());

        // La validación redundante ya no existe — branchRepository no debe ni consultarse.
        verifyNoInteractions(branchRepository);
    }

    @Test
    void update_noOwnerDeLaCancha_retorna403() throws Exception {
        when(courtRepository.existsByCourtAndOwner(anyLong(), anyLong())).thenReturn(false);

        mockMvc.perform(put("/api/courts/owner/1").with(owner(1L))
                        .contentType("application/json")
                        .content("{\"name\": \"Cancha renombrada\", \"pricePerHour\": 100, \"idBranch\": 20, \"idSport\": 5}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(updateCourtUseCase);
    }
}
