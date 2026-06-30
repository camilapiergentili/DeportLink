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
    List<ClubResponseDto> getByActiveAndApproved();
    Page<ClubResponseDto> getByActiveAndApprovedPaginated(Pageable pageable);
    List<ClubResponseDto> getAll();
    Page<ClubResponseDto> getAllPaginated(Pageable pageable);
    ClubResponseDto create(ClubRequestDto clubDto);
    void delete(long id);
    void update(long id, ClubRequestDto clubDto);
}
