package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.dto.request.AddressRequestDto;
import com.deportlink.DeportLink.mapper.AddressMapper;
import com.deportlink.DeportLink.persistence.repository.AddressRepository;
import com.deportlink.DeportLink.service.AddressService;
import org.springframework.stereotype.Service;

@Service
public class AddressServiceImplementation implements AddressService {

    private AddressMapper addressMapper;
    private AddressRepository addressRepository;

    public void add(AddressRequestDto addressDto) {

    }

}
