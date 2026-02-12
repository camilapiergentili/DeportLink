package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
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

        AddressEntity address = addressMapper.toModel(playerDto.getAddressRequestDto());

        playerEntity.getAddresses().add(address);

        playerRepository.save(playerEntity);

        return playerMapper.toResponse(playerEntity);
    }

    public void delete(long idPlayer){

        PlayerEntity playerEntity = getById(idPlayer);
        playerRepository.delete(playerEntity);
    }
}
