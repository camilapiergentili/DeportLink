package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.AddressRequestDto;
import com.deportlink.deportlink.exception.AddressNotFoundException;
import com.deportlink.deportlink.mapper.AddressMapper;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.persistence.repository.AddressRepository;
import com.deportlink.deportlink.service.AddressService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
public class AddressServiceImplementation implements AddressService {

    private PlayerServiceImplementation playerService;
    private AddressMapper addressMapper;
    private AddressRepository addressRepository;

    @Override
    @Transactional
    public void addAddress(long idPlayer, AddressRequestDto addressDto) {
        log.info("Adding address for player: playerId={}, city={}", idPlayer, addressDto.getCity());

        try {
            PlayerEntity player = playerService.getById(idPlayer);
            AddressEntity address = addressMapper.toModel(addressDto);

            //sí es la primera dirección, setearla como default automáticamente
            if (player.getAddresses().isEmpty()) {
                address.setDefault(true);
            }

            player.getAddresses().add(address);
            playerService.save(player);
            log.info("Address added successfully for player: playerId={}, addressId={}", idPlayer, address.getId());
        } catch (Exception e) {
            log.error("Failed to add address for player {}: {}", idPlayer, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void setDefaultAddress(long idPlayer, long idAddress) {
        log.info("Setting default address for player: playerId={}, addressId={}", idPlayer, idAddress);

        try {
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
            log.info("Default address set successfully for player: playerId={}, addressId={}", idPlayer, idAddress);
        } catch (Exception e) {
            log.error("Failed to set default address for player {}: {}", idPlayer, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void deleteAddress(long idPlayer, long idAddress) {
        log.info("Deleting address for player: playerId={}, addressId={}", idPlayer, idAddress);

        try {
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
            log.info("Address deleted successfully for player: playerId={}, addressId={}", idPlayer, idAddress);
        } catch (Exception e) {
            log.error("Failed to delete address for player {}: {}", idPlayer, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void updateAddress(long idPlayer, long idAddress, AddressRequestDto addressDto){
        log.info("Updating address for player: playerId={}, addressId={}, city={}", idPlayer, idAddress, addressDto.getCity());

        try {
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
            log.info("Address updated successfully for player: playerId={}, addressId={}", idPlayer, idAddress);
        } catch (Exception e) {
            log.error("Failed to update address for player {}: {}", idPlayer, e.getMessage(), e);
            throw e;
        }
    }

}
