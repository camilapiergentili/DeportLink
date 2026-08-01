package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.ClubRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.model.entity.ClubEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ClubService {
    ClubEntity getById(long id);
    ClubResponseDto getByIdResponse(long id);
    Page<ClubResponseDto> getByActiveAndApprovedPaginated(Pageable pageable);
    Page<ClubResponseDto> getAllPaginated(Pageable pageable);
    List<ClubResponseDto> getAll();
    void approve(long idClub);
    void reject(long idClub);
}
