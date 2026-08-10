package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.club.GetAllClubsUseCase;
import com.deportlink.deportlink.application.usecase.club.GetApprovedClubsUseCase;
import com.deportlink.deportlink.application.usecase.club.GetClubUseCase;
import com.deportlink.deportlink.application.usecase.club.SearchClubsByNameUseCase;
import com.deportlink.deportlink.domain.port.out.PageRequest;
import com.deportlink.deportlink.domain.port.out.PageResult;
import com.deportlink.deportlink.mapper.dto.ClubMapper;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
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
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final GetClubUseCase getClubUseCase;
    private final GetApprovedClubsUseCase getApprovedClubsUseCase;
    private final GetAllClubsUseCase getAllClubsUseCase;
    private final SearchClubsByNameUseCase searchClubsByNameUseCase;
    private final ClubMapper clubMapper;

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubResponseDto> getById(@PathVariable long id) {
        return ResponseEntity.ok(clubMapper.toResponse(getClubUseCase.execute(id)));
    }

    @GetMapping("/approved")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ClubResponseDto>> getApproved(
            @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        PageRequest pageRequest = toPageRequest(pageable);
        Page<ClubResponseDto> result = toPage(
                getApprovedClubsUseCase.execute(pageRequest), pageRequest, clubMapper::toResponse);
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ClubResponseDto>> getAll(
            @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        PageRequest pageRequest = toPageRequest(pageable);
        Page<ClubResponseDto> result = toPage(
                getAllClubsUseCase.execute(pageRequest), pageRequest, clubMapper::toResponse);
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ClubResponseDto>> search(
            @RequestParam String name,
            @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        PageRequest pageRequest = toPageRequest(pageable);
        Page<ClubResponseDto> result = toPage(
                searchClubsByNameUseCase.execute(name, pageRequest), pageRequest, clubMapper::toResponse);
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
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