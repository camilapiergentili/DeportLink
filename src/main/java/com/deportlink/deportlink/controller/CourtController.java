package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.court.*;
import com.deportlink.deportlink.domain.port.out.PageRequest;
import com.deportlink.deportlink.domain.port.out.PageResult;
import com.deportlink.deportlink.mapper.dto.CourtMapper;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Function;

@RestController
@RequestMapping("/api/courts")
@RequiredArgsConstructor
public class CourtController {

    private final GetCourtByIdUseCase getCourtByIdUseCase;
    private final GetActiveCourtUseCase getActiveCourtUseCase;
    private final GetApprovedCourtsUseCase getApprovedCourtsUseCase;
    private final GetApprovedCourtsByBranchUseCase getApprovedCourtsByBranchUseCase;
    private final GetCourtsByBranchAndSportUseCase getCourtsByBranchAndSportUseCase;
    private final CourtMapper courtMapper;

    @GetMapping("/{idCourt}")
    @PreAuthorize("""
        hasRole('ADMIN')
        or (hasRole('OWNER') and @courtAuthorization.isOwnerOfCourt(#idCourt, authentication))
    """)
    public ResponseEntity<CourtResponseDto> getById(@PathVariable long idCourt) {
        return ResponseEntity.ok(courtMapper.toResponse(getCourtByIdUseCase.execute(idCourt)));
    }

    @GetMapping("/{idCourt}/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CourtResponseDto> getByIdApprovedAndActive(@PathVariable long idCourt) {
        return ResponseEntity.ok(courtMapper.toResponse(getActiveCourtUseCase.execute(idCourt)));
    }

    @GetMapping("/branch/{idBranch}/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CourtResponseDto>> getAllByBranchActiveAndApproved(@PathVariable long idBranch) {
        List<CourtResponseDto> result = getApprovedCourtsByBranchUseCase
                .execute(idBranch, PageRequest.unpaged()).content()
                .stream().map(courtMapper::toResponse).toList();
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/branch/{idBranch}/active/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<CourtResponseDto>> getAllByBranchActiveAndApprovedPaginated(
            @PathVariable long idBranch,
            @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        PageRequest pageRequest = toPageRequest(pageable);
        Page<CourtResponseDto> result = toPage(
                getApprovedCourtsByBranchUseCase.execute(idBranch, pageRequest),
                pageRequest, courtMapper::toResponse);
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CourtResponseDto>> getAllActiveAndApproved() {
        List<CourtResponseDto> result = getApprovedCourtsUseCase
                .execute(PageRequest.unpaged()).content()
                .stream().map(courtMapper::toResponse).toList();
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/active/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<CourtResponseDto>> getAllActiveAndApprovedPaginated(
            @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        PageRequest pageRequest = toPageRequest(pageable);
        Page<CourtResponseDto> result = toPage(
                getApprovedCourtsUseCase.execute(pageRequest), pageRequest, courtMapper::toResponse);
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/branch/{idBranch}/sport/{idSport}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CourtResponseDto>> getCourtsByBranchAndSport(
            @PathVariable long idBranch,
            @PathVariable long idSport) {
        List<CourtResponseDto> result = getCourtsByBranchAndSportUseCase
                .execute(idBranch, idSport).stream().map(courtMapper::toResponse).toList();
        return ResponseEntity.ok(result);
    }

    // ─── Spring Data Pageable/Page ↔ dominio PageRequest/PageResult ────────────
    // Los controllers son adaptadores primarios: traducen el mundo externo (query params)
    // al lenguaje del dominio, y el resultado del dominio de vuelta a lo que el cliente espera.

    private PageRequest toPageRequest(Pageable pageable) {
        Sort.Order order = pageable.getSort().stream().findFirst().orElse(null);
        return new PageRequest(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                order != null ? order.getProperty() : null,
                order == null || order.isAscending()
        );
    }

    /**
     * Reconstruye el Page de respuesta a partir del Sort que efectivamente se aplicó en la
     * consulta (el del PageRequest de dominio, un solo campo) — no del Pageable original del
     * request, que puede pedir varios campos de sort que nuestro dominio no soporta todavía.
     * Si se reconstruyera con el Pageable original, la respuesta reportaría un sort que nunca
     * se aplicó de verdad.
     */
    private <D, R> Page<R> toPage(PageResult<D> result, PageRequest pageRequest, Function<D, R> mapper) {
        List<R> content = result.content().stream().map(mapper).toList();
        Sort effectiveSort = pageRequest.sortBy() == null
                ? Sort.unsorted()
                : Sort.by(pageRequest.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, pageRequest.sortBy());
        Pageable effectivePageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.page(), pageRequest.size(), effectiveSort);
        return new PageImpl<>(content, effectivePageable, result.totalElements());
    }
}