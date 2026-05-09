package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.ClubRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.model.entity.ClubEntity;

import java.util.List;

public interface ClubService {
    ClubEntity getById(long id);
    ClubResponseDto getByIdResponse(long id);
    List<ClubResponseDto> getByActiveAndApproved();
    List<ClubResponseDto> getAll();
    ClubResponseDto create(ClubRequestDto clubDto);
    void delete(long id);
    void update(long id, ClubRequestDto clubDto);
}
