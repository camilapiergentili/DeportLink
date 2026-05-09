package com.deportlink.deportlink.service;


import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;

public interface ClubOwnerService {
    void addOwner(long idClub, OwnerRequestDto ownerDto) throws OwnerAlreadyExistsException;
    void deleteOwner(long idClub, long idOwner);
    void activate(long idOwner, long idClub);
    void deactivate(long idOwner, long idClub);
}
