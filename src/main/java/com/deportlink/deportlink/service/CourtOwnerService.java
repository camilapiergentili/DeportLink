package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.CourtRequestDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.exception.NegativePriceException;

public interface CourtOwnerService {
    CourtResponseDto create(CourtRequestDto courtDto);
    CourtResponseDto update(long idCourt, CourtRequestDto courtDto);
    void updatePrice(long idCourt, double newPrice) throws NegativePriceException;
    void delete(long idCourt);
    void activateCourt(long idBranch, long idCourt);
    void desactivedCourt(long idBranch, long idCourt);
}
