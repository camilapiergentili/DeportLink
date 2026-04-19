package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.CourtRequestDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;

public interface CourtOwnerService {

    CourtResponseDto create(CourtRequestDto courtDto);
    void delete(long idCourt);
    CourtResponseDto update(long idCourt, CourtRequestDto courtDto);
    void updatePrice(long idCourt, double newPrice);
    void activateCourt(long idBranch, long idCourt);
    void desactivedCourt(long idBranch, long idCourt);
}
