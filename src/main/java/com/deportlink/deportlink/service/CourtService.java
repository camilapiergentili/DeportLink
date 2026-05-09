package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.model.entity.CourtEntity;

import java.util.List;


public interface CourtService {
    CourtResponseDto getByIdResponse(long idCourt);
    CourtResponseDto getByIdApprovedAndActive(long idCourt);
    List<CourtResponseDto> getAllActiveAndApproved();
    List<CourtResponseDto> getAllByBranchActiveAndApproved(long idBranch);
    List<CourtResponseDto> getCourtsByBranchAndSport(long idBranch, long idSport);
}
