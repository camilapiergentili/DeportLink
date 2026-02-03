package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.dto.request.PlayerRequestDto;
import com.deportlink.DeportLink.dto.response.PlayerResponseDto;
import com.deportlink.DeportLink.exception.PlayerAlreadyExistsException;
import com.deportlink.DeportLink.exception.UserNotFoundException;
import com.deportlink.DeportLink.mapper.AddressMapper;
import com.deportlink.DeportLink.mapper.PlayerMapper;
import com.deportlink.DeportLink.model.entity.AddressEntity;
import com.deportlink.DeportLink.model.entity.PlayerEntity;
import com.deportlink.DeportLink.persistence.repository.PlayerRepository;
import com.deportlink.DeportLink.service.PlayerService;
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
