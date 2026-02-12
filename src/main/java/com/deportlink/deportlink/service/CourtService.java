package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.CourtRequestDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.model.entity.CourtEntity;

import java.util.List;


public interface CourtService {
    void create(CourtRequestDto courtDto);
    CourtEntity getById(long idCourt);
    CourtEntity getCourtByIdWithSchedule(long idCourt);
    void delete(long idCourt);
    CourtResponseDto update(long idCourt, CourtRequestDto courtDto);
    void updatePrice(long idCourt, double newPrice);
    void activateCourt(long idBranch, long idCourt);
    void desactivedCourt(long idBranch, long idCourt);
    List<CourtResponseDto> getAll();

}
