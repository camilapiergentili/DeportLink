package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.branch.GetApprovedBranchesUseCase;
import com.deportlink.deportlink.application.usecase.branch.GetBranchUseCase;
import com.deportlink.deportlink.application.usecase.branch.GetBranchesBySportUseCase;
import com.deportlink.deportlink.application.usecase.branch.GetNearbyBranchesUseCase;
import com.deportlink.deportlink.application.usecase.branch.SearchBranchesByNameUseCase;
import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.dto.response.AddressResponseDto;
import com.deportlink.deportlink.dto.response.BranchResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final GetBranchUseCase getBranchUseCase;
    private final GetApprovedBranchesUseCase getApprovedBranchesUseCase;
    private final SearchBranchesByNameUseCase searchBranchesByNameUseCase;
    private final GetBranchesBySportUseCase getBranchesBySportUseCase;
    private final GetNearbyBranchesUseCase getNearbyBranchesUseCase;

    @GetMapping("/{idBranch}/approved")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BranchResponseDto> getApprovedById(@PathVariable long idBranch) {
        return ResponseEntity.ok(toResponse(getBranchUseCase.execute(idBranch)));
    }

    @GetMapping("/{idClub}/active-approved")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BranchResponseDto>> getApprovedAndActiveByClub(@PathVariable long idClub) {
        List<BranchResponseDto> result = getApprovedBranchesUseCase.execute(idClub)
                .stream().map(this::toResponse).toList();
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BranchResponseDto>> search(@RequestParam String name) {
        List<BranchResponseDto> result = searchBranchesByNameUseCase.execute(name)
                .stream().map(this::toResponse).toList();
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/by-sport/{sportId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BranchResponseDto>> getBySport(@PathVariable Long sportId) {
        List<BranchResponseDto> result = getBranchesBySportUseCase.execute(sportId)
                .stream().map(this::toResponse).toList();
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/nearby")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BranchResponseDto>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") double radiusKm) {
        List<BranchResponseDto> result = getNearbyBranchesUseCase.execute(lat, lng, radiusKm)
                .stream().map(this::toResponse).toList();
        return result.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }

    BranchResponseDto toResponse(Branch branch) {
        BranchResponseDto dto = new BranchResponseDto();
        dto.setId(branch.id());
        dto.setName(branch.name());
        dto.setClubId(branch.clubId());
        dto.setVerificationStatus(branch.verificationStatus());
        dto.setActiveStatus(branch.activeStatus());
        dto.setAddress(toAddressResponse(branch.address()));
        return dto;
    }

    private AddressResponseDto toAddressResponse(Address address) {
        if (address == null) return null;
        AddressResponseDto dto = new AddressResponseDto();
        dto.setStreetName(address.streetName());
        dto.setNumber(address.number());
        dto.setCity(address.city());
        dto.setProvince(address.province());
        dto.setPostalCode(address.postalCode());
        dto.setLatitude(address.latitude());
        dto.setLongitude(address.longitude());
        return dto;
    }
}