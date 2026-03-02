package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.AddressRequestDto;
import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import com.deportlink.deportlink.exception.AddressNotFoundException;
import com.deportlink.deportlink.exception.PlayerAlreadyExistsException;
import com.deportlink.deportlink.exception.UserNotFoundException;
import com.deportlink.deportlink.mapper.AddressMapper;
import com.deportlink.deportlink.mapper.PlayerMapper;
import com.deportlink.deportlink.model.Rol;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.persistence.repository.PlayerRepository;
import com.deportlink.deportlink.service.PlayerService;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@AllArgsConstructor
public class PlayerServiceImplementation implements PlayerService {

    private final PlayerMapper playerMapper;
    private final AddressMapper addressMapper;
    private final PasswordEncoder passwordEncoder;
    private final PlayerRepository playerRepository;

    public PlayerResponseDto register(PlayerRequestDto playerDto){

        PlayerEntity playerEntity = playerMapper.toModel(playerDto);

        if(playerRepository.findByEmail(playerEntity.getEmail()).isPresent()){
            throw new PlayerAlreadyExistsException("El email " + playerEntity.getEmail() + " ya se encuentra registrado");
        }

        playerEntity.setPassword(passwordEncoder.encode(playerDto.getPassword()));
        playerEntity.setRole(Rol.PLAYER);

        playerEntity.getAddresses().forEach(address -> address.setDefault(true));

        playerRepository.save(playerEntity);

        return playerMapper.toResponse(playerEntity);
    }

    public PlayerEntity getById(long idPlayer){
        return playerRepository.findById(idPlayer)
                .orElseThrow(() -> new UserNotFoundException("El jugador no se encontro"));
    }

    public PlayerResponseDto getByIdResponse(long idPlayer){
        PlayerEntity playerEntity = getById(idPlayer);
        return playerMapper.toResponse(playerEntity);
    }

    public PlayerResponseDto update(long idPlayer, PlayerRequestDto playerDto){
        PlayerEntity playerEntity = getById(idPlayer);

        playerEntity.setFirstName(playerDto.getFirstName());
        playerEntity.setLastName(playerDto.getLastName());
        playerEntity.setEmail(playerDto.getEmail());
        playerEntity.setPhone(playerDto.getPhone());

        playerRepository.save(playerEntity);

        return playerMapper.toResponse(playerEntity);
    }

    public void delete(long idPlayer){

        PlayerEntity playerEntity = getById(idPlayer);
        playerRepository.delete(playerEntity);
    }

    public void addAddress(long idPlayer, AddressRequestDto addressDto){
        PlayerEntity player = getById(idPlayer);
        AddressEntity address = addressMapper.toModel(addressDto);

        // si es la primera dirección, setearla como default automáticamente
        if(player.getAddresses().isEmpty()){
            address.setDefault(true);
        }

        player.getAddresses().add(address);
        playerRepository.save(player);
    }

    public void setDefaultAddress(long idPlayer, long idAddress){
        PlayerEntity player = getById(idPlayer);

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

        playerRepository.save(player);
    }

    public void deleteAddress(long idPlayer, long idAddress){
        PlayerEntity player = getById(idPlayer);

        AddressEntity address = player.getAddresses()
                .stream()
                .filter(a -> a.getId() == idAddress)
                .findFirst()
                .orElseThrow(() -> new AddressNotFoundException("La dirección no pertenece al jugador"));

        if(address.isDefault() && player.getAddresses().size() > 1){
            throw new IllegalStateException("No puedes eliminar la dirección default, primero seleccioná otra como default");
        }

        player.getAddresses().remove(address);
        playerRepository.save(player);
    }

    public void updateAddress(long idPlayer, long idAddress, AddressRequestDto addressDto){
        PlayerEntity player = getById(idPlayer);

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

        playerRepository.save(player);
    }

}
