package com.deportlink.deportlink.service;


import com.deportlink.deportlink.dto.request.ClubRequestDto;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;

public interface ClubOwnerService {
    ClubResponseDto create(ClubRequestDto clubDto);
    void delete(long id);
    void update(long id, ClubRequestDto clubDto);
    void addOwner(long idClub, OwnerRequestDto ownerDto) throws OwnerAlreadyExistsException;
    void deleteOwner(long idClub, long idOwner);
    void activate(long idClub);
    void deactivate(long idClub);
}
