package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.AddressRequestDto;

public interface AddressService {
    void addAddress(long idPlayer, AddressRequestDto addressDto);
    void setDefaultAddress(long idPlayer, long idAddress);
    void deleteAddress(long idPlayer, long idAddress);
    void updateAddress(long idPlayer, long idAddress, AddressRequestDto addressDto);
}
