package com.deportlink.DeportLink.service;

import com.deportlink.DeportLink.dto.request.ClubRequestDto;
import com.deportlink.DeportLink.dto.request.OwnerRequestDto;
import com.deportlink.DeportLink.dto.response.ClubResponseDto;
import com.deportlink.DeportLink.exception.OwnerAlreadyExistsException;
import com.deportlink.DeportLink.model.entity.ClubEntity;

import java.util.List;

public interface ClubService {

    void createClub(ClubRequestDto clubDto);
    ClubEntity getById(long id);
    ClubResponseDto getByIdResponse(long id);
    List<ClubResponseDto> getAllClubsActiveAndApproved();
    List<ClubResponseDto> getAll();
    void delete(long id);
    void update(long id, ClubRequestDto clubDto);
    void addOwnerToClub(long idClub, OwnerRequestDto ownerDto) throws OwnerAlreadyExistsException;
    void deleteOwnerToClub(long idClub, long idOwner);
    void deactivateClub(long idOwner, long idClub);
    void activateClub(long idOwner, long idClub);
}
