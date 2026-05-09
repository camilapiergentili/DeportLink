package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import com.deportlink.deportlink.exception.PlayerAlreadyExistsException;
import com.deportlink.deportlink.exception.UserNotFoundException;
import com.deportlink.deportlink.mapper.PlayerMapper;
import com.deportlink.deportlink.model.Rol;
import com.deportlink.deportlink.model.StatusReservation;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.persistence.repository.PlayerRepository;
import com.deportlink.deportlink.service.PlayerService;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@AllArgsConstructor
public class PlayerServiceImplementation implements PlayerService {

    private final PlayerMapper playerMapper;
    private final PasswordEncoder passwordEncoder;
    private final PlayerRepository playerRepository;

    @Override
    @Transactional
    public PlayerResponseDto register(PlayerRequestDto playerDto){

        PlayerEntity playerEntity = playerMapper.toModel(playerDto);

        if(playerRepository.findByEmail(playerEntity.getEmail()).isPresent()){
            throw new PlayerAlreadyExistsException("El email " + playerEntity.getEmail() + " ya se encuentra registrado");
        }

        playerEntity.setPassword(passwordEncoder.encode(playerDto.getPassword()));
        playerEntity.setRole(Rol.PLAYER);

        playerEntity.getAddresses().forEach(address -> address.setDefault(true));

        save(playerEntity);

        return playerMapper.toResponse(playerEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public PlayerEntity getById(long idPlayer){
        return playerRepository.findById(idPlayer)
                .orElseThrow(() -> new UserNotFoundException("El jugador no se encontro"));
    }

    @Override
    @Transactional(readOnly = true)
    public PlayerResponseDto getByIdResponse(long idPlayer){
        PlayerEntity playerEntity = getById(idPlayer);
        return playerMapper.toResponse(playerEntity);
    }

    @Override
    @Transactional
    public PlayerResponseDto update(long idPlayer, PlayerRequestDto playerDto){
        PlayerEntity playerEntity = getById(idPlayer);

        emailAlreadyExists(playerDto.getEmail(), playerEntity.getId());

        playerEntity.setFirstName(playerDto.getFirstName());
        playerEntity.setLastName(playerDto.getLastName());
        playerEntity.setEmail(playerDto.getEmail());
        playerEntity.setPhone(playerDto.getPhone());

        save(playerEntity);

        return playerMapper.toResponse(playerEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public void delete(long idPlayer){
        PlayerEntity playerEntity = getById(idPlayer);

        // Cancelar reservas activas antes de eliminar
        playerEntity.getReservations()
                .stream()
                .filter(r -> r.getStatus().equals(StatusReservation.RESERVADO) ||
                        r.getStatus().equals(StatusReservation.REPROGRAMADO))
                .forEach(r -> r.setStatus(StatusReservation.CANCELADO));

        save(playerEntity); // guarda las reservas canceladas
        playerRepository.delete(playerEntity);
    }


    private void save(PlayerEntity playerEntity){
        playerRepository.save(playerEntity);
    }

    private void emailAlreadyExists(String email, long idPlayer){
        playerRepository.findByEmail(email)
                .ifPresent(existing -> {
                    if(existing.getId() != idPlayer){
                        throw new PlayerAlreadyExistsException("El email ya está en uso");
                    }
                });
    }


}
