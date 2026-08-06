package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.club.*;
import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.port.out.PageRequest;
import com.deportlink.deportlink.domain.port.out.PageResult;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.ClubType;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.mapper.dto.ClubMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test unitario directo (sin Spring, sin MockMvc): se instancia el controller a mano.
 * <p>
 * Motivo: Spring Data no expone los campos/orden de un {@link Sort} al serializarlo a JSON —
 * solo serializa {@code empty}/{@code sorted}/{@code unsorted} (booleanos). Verificado
 * empíricamente: pedir {@code ?sort=name,asc&sort=cuit,desc} y reconstruir el Page de
 * respuesta con el Pageable original (2 campos) vs. con el PageRequest de dominio (1 campo)
 * produce el MISMO JSON — la "mentira" del sort que motivó este cambio no es observable vía
 * HTTP con la config de serialización actual del proyecto. Sí es observable inspeccionando el
 * objeto Page en Java antes de serializar, que es lo que este test hace.
 */
class ClubControllerTest {

    private final GetClubUseCase getClubUseCase = mock(GetClubUseCase.class);
    private final GetApprovedClubsUseCase getApprovedClubsUseCase = mock(GetApprovedClubsUseCase.class);
    private final GetAllClubsUseCase getAllClubsUseCase = mock(GetAllClubsUseCase.class);
    private final SearchClubsByNameUseCase searchClubsByNameUseCase = mock(SearchClubsByNameUseCase.class);
    private final ClubMapper clubMapper = mock(ClubMapper.class);

    private ClubController controller;

    @BeforeEach
    void setUp() {
        controller = new ClubController(
                getClubUseCase, getApprovedClubsUseCase, getAllClubsUseCase, searchClubsByNameUseCase, clubMapper);
        when(clubMapper.toResponse(any(Club.class))).thenReturn(new ClubResponseDto());
    }

    private static Club club() {
        return new Club(1L, "Club Norte", "Club Norte SA", "30111111111",
                ClubType.SA, VerificationStatus.APPROVED, ActiveStatus.ACTIVE, Set.of(10L));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Honestidad del sort en la paginación: el dominio solo soporta un campo de orden.
    // Si el cliente pide varios (?sort=name,asc&sort=cuit,desc), el Page de respuesta debe
    // reportar únicamente el que efectivamente viajó al use case — no el pedido completo.
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void getApproved_sortMultiCampo_elPageDeRespuestaSoloReportaElCampoRealmenteAplicado() {
        Sort multiSort = Sort.by(Sort.Order.asc("name"), Sort.Order.desc("cuit"));
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 12, multiSort);

        when(getApprovedClubsUseCase.execute(any(PageRequest.class)))
                .thenReturn(PageResult.of(List.of(club()), 1L, 1, 0));

        ResponseEntity<Page<ClubResponseDto>> response = controller.getApproved(pageable);

        // El use case recibió el PageRequest de dominio con un solo campo (el primero pedido)
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(getApprovedClubsUseCase).execute(captor.capture());
        assertEquals("name", captor.getValue().sortBy());
        assertTrue(captor.getValue().ascending());

        // El Page de respuesta reporta ese mismo único campo — no "cuit", que el cliente pidió
        // pero nunca se aplicó a la consulta real.
        List<Sort.Order> reportedOrders = response.getBody().getSort().stream().toList();
        assertEquals(1, reportedOrders.size(),
                "La respuesta no debe reportar más campos de sort que los realmente aplicados");
        assertEquals("name", reportedOrders.get(0).getProperty());
        assertTrue(reportedOrders.get(0).isAscending());
    }
}
