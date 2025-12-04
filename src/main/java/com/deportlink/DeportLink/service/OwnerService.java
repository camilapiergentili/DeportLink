package com.deportlink.DeportLink.service;

import com.deportlink.DeportLink.dto.request.OwnerRequestDto;
import com.deportlink.DeportLink.dto.response.ClubResponseDto;
import com.deportlink.DeportLink.dto.response.OwnerResponseDto;
import com.deportlink.DeportLink.exception.OwnerAlreadyExistsException;
import com.deportlink.DeportLink.model.entity.OwnerEntity;

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
