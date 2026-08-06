package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.club.GetAllClubsUseCase;
import com.deportlink.deportlink.application.usecase.club.GetApprovedClubsUseCase;
import com.deportlink.deportlink.application.usecase.club.GetClubUseCase;
import com.deportlink.deportlink.application.usecase.club.SearchClubsByNameUseCase;
import com.deportlink.deportlink.mapper.dto.ClubMapper;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
        Page<ClubResponseDto> result = getApprovedClubsUseCase.execute(pageable).map(clubMapper::toResponse);
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ClubResponseDto>> getAll(
            @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        Page<ClubResponseDto> result = getAllClubsUseCase.execute(pageable).map(clubMapper::toResponse);
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ClubResponseDto>> search(
            @RequestParam String name,
            @PageableDefault(size = 12, sort = "id") Pageable pageable) {
        Page<ClubResponseDto> result = searchClubsByNameUseCase.execute(name, pageable).map(clubMapper::toResponse);
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }
}