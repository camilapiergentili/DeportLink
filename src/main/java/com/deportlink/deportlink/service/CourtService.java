package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.model.entity.CourtEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;


public interface CourtService {
    CourtResponseDto getByIdResponse(long idCourt);
    CourtResponseDto getByIdApprovedAndActive(long idCourt);
    List<CourtResponseDto> getAllActiveAndApproved();
    Page<CourtResponseDto> getAllActiveAndApprovedPaginated(Pageable pageable);
    List<CourtResponseDto> getAllByBranchActiveAndApproved(long idBranch);
    Page<CourtResponseDto> getAllByBranchActiveAndApprovedPaginated(long idBranch, Pageable pageable);
    List<CourtResponseDto> getCourtsByBranchAndSport(long idBranch, long idSport);
}
