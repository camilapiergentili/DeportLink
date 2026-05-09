package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.AddressRequestDto;
import com.deportlink.deportlink.exception.AddressNotFoundException;
import com.deportlink.deportlink.mapper.AddressMapper;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.persistence.repository.AddressRepository;
import com.deportlink.deportlink.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressServiceImplementation implements AddressService {

    private PlayerServiceImplementation playerService;
    private AddressMapper addressMapper;
    private AddressRepository addressRepository;

    @Override
    @Transactional
    public void addAddress(long idPlayer, AddressRequestDto addressDto) {
        PlayerEntity player = playerService.getById(idPlayer);
        AddressEntity address = addressMapper.toModel(addressDto);

        // si es la primera dirección, setearla como default automáticamente
        if (player.getAddresses().isEmpty()) {
            address.setDefault(true);
        }

        player.getAddresses().add(address);
        playerService.save(player);
    }

    @Override
    @Transactional
    public void setDefaultAddress(long idPlayer, long idAddress) {
        PlayerEntity player = playerService.getById(idPlayer);

        // desmarcar la anterior
        player.getAddresses()
                .forEach(a -> a.setDefault(false));

        // marcar la nueva
        player.getAddresses()
                .stream()
                .filter(a -> a.getId() == idAddress)
                .findFirst()
                .orElseThrow(() -> new AddressNotFoundException("La dirección no pertenece al jugador"))
                .setDefault(true);

        playerService.save(player);
    }

    @Override
    @Transactional
    public void deleteAddress(long idPlayer, long idAddress) {
        PlayerEntity player = playerService.getById(idPlayer);

        AddressEntity address = player.getAddresses()
                .stream()
                .filter(a -> a.getId() == idAddress)
                .findFirst()
                .orElseThrow(() -> new AddressNotFoundException("La dirección no pertenece al jugador"));

        if (player.getAddresses().size() == 1) {
            throw new IllegalStateException("El jugador debe tener al menos una dirección");
        }

        if (address.isDefault()) {
            throw new IllegalStateException("No puedes eliminar la dirección default, primero seleccioná otra como default");
        }

        player.getAddresses().remove(address);
        playerService.save(player);
    }

    @Override
    @Transactional
    public void updateAddress(long idPlayer, long idAddress, AddressRequestDto addressDto){
        PlayerEntity player = playerService.getById(idPlayer);

        AddressEntity address = player.getAddresses()
                .stream()
                .filter(a -> a.getId() == idAddress)
                .findFirst()
                .orElseThrow(() -> new AddressNotFoundException("La dirección no pertenece al jugador"));

        address.setStreetName(addressDto.getStreetName());
        address.setNumber(addressDto.getNumber());
        address.setCity(addressDto.getCity());
        address.setProvince(addressDto.getProvince());
        address.setPostalCode(addressDto.getPostalCode());
        address.setLatitude(addressDto.getLatitude());
        address.setLongitude(addressDto.getLongitude());

        playerService.save(player);
    }

}
