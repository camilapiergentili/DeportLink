package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.court.*;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.PageRequest;
import com.deportlink.deportlink.domain.port.out.PageResult;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.mapper.dto.CourtMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test unitario directo (sin Spring, sin MockMvc) — mismo motivo que ClubControllerTest:
 * Spring Data no expone los campos de un Sort al serializarlo a JSON, así que la honestidad
 * del sort solo es verificable inspeccionando el Page en Java antes de serializar.
 */
class CourtControllerTest {

    private final GetCourtByIdUseCase getCourtByIdUseCase = mock(GetCourtByIdUseCase.class);
    private final GetActiveCourtUseCase getActiveCourtUseCase = mock(GetActiveCourtUseCase.class);
    private final GetApprovedCourtsUseCase getApprovedCourtsUseCase = mock(GetApprovedCourtsUseCase.class);
    private final GetApprovedCourtsByBranchUseCase getApprovedCourtsByBranchUseCase =
            mock(GetApprovedCourtsByBranchUseCase.class);
    private final GetCourtsByBranchAndSportUseCase getCourtsByBranchAndSportUseCase =
            mock(GetCourtsByBranchAndSportUseCase.class);
    private final CourtMapper courtMapper = mock(CourtMapper.class);

    private CourtController controller;

    @BeforeEach
    void setUp() {
        controller = new CourtController(getCourtByIdUseCase, getActiveCourtUseCase, getApprovedCourtsUseCase,
                getApprovedCourtsByBranchUseCase, getCourtsByBranchAndSportUseCase, courtMapper);
        when(courtMapper.toResponse(any(Court.class))).thenReturn(new CourtResponseDto());
    }

    private static Court court() {
        return new Court(1L, "Cancha 1", 100.0, 10L, 5L, "Football", ActiveStatus.ACTIVE);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Honestidad del sort en la paginación: el dominio solo soporta un campo de orden.
    // Si el cliente pide varios (?sort=name,asc&sort=pricePerHour,desc), el Page de respuesta
    // debe reportar únicamente el que efectivamente viajó al use case.
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void getAllActiveAndApprovedPaginated_sortMultiCampo_elPageDeRespuestaSoloReportaElCampoRealmenteAplicado() {
        Sort multiSort = Sort.by(Sort.Order.asc("name"), Sort.Order.desc("pricePerHour"));
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 12, multiSort);

        when(getApprovedCourtsUseCase.execute(any(PageRequest.class)))
                .thenReturn(PageResult.of(List.of(court()), 1L, 1, 0));

        ResponseEntity<Page<CourtResponseDto>> response = controller.getAllActiveAndApprovedPaginated(pageable);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(getApprovedCourtsUseCase).execute(captor.capture());
        assertEquals("name", captor.getValue().sortBy());
        assertTrue(captor.getValue().ascending());

        List<Sort.Order> reportedOrders = response.getBody().getSort().stream().toList();
        assertEquals(1, reportedOrders.size(),
                "La respuesta no debe reportar más campos de sort que los realmente aplicados");
        assertEquals("name", reportedOrders.get(0).getProperty());
        assertTrue(reportedOrders.get(0).isAscending());
    }
}
