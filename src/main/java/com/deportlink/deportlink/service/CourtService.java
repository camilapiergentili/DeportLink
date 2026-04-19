package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.CourtRequestDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.model.entity.CourtEntity;

import java.util.List;


public interface CourtService {

    CourtEntity getById(long idCourt);
    CourtResponseDto getByIdResponse(long idCourt);
    CourtEntity getCourtByIdWithSchedule(long idCourt);
    CourtResponseDto getByIdApprovedAndActive(long idCourt);
    List<CourtResponseDto> getAllBranch(long idBranch);
    List<CourtResponseDto> getAllActiveAndApproved();
    List<CourtResponseDto> getAllByBranchActiveAndApproved(long idBranch);
    List<CourtResponseDto> getCourtsByBranchAndSport(long idBranch, long idSport);

}
