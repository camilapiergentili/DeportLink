package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.dto.response.OwnerResponseDto;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;
import com.deportlink.deportlink.model.entity.OwnerEntity;

import java.util.List;

public interface OwnerService {

    OwnerResponseDto register(OwnerRequestDto ownerDto) throws OwnerAlreadyExistsException;
    void deleteById(long id);
    OwnerEntity getById(long id);
    OwnerResponseDto getByIdResponse(long id);
    void update(long id, OwnerRequestDto ownerDto);
    List<OwnerResponseDto> getAll();
    List<ClubResponseDto> getAllClubsByOwner(long idOwner);

}
