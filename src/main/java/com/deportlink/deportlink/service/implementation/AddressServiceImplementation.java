package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.AddressRequestDto;
import com.deportlink.deportlink.mapper.AddressMapper;
import com.deportlink.deportlink.persistence.repository.AddressRepository;
import com.deportlink.deportlink.service.AddressService;
import org.springframework.stereotype.Service;

@Service
public class AddressServiceImplementation implements AddressService {

    private AddressMapper addressMapper;
    private AddressRepository addressRepository;

    public void add(AddressRequestDto addressDto) {

    }

}
