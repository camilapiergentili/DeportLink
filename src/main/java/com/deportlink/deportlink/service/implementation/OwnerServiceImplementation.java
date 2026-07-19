package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.dto.response.OwnerResponseDto;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;
import com.deportlink.deportlink.exception.UnderageException;
import com.deportlink.deportlink.exception.OwnerNotFoundException;
import com.deportlink.deportlink.mapper.ClubMapper;
import com.deportlink.deportlink.mapper.OwnerMapper;
import com.deportlink.deportlink.model.Rol;
import com.deportlink.deportlink.model.entity.ClubEntity;
import com.deportlink.deportlink.model.entity.OwnerEntity;
import com.deportlink.deportlink.persistence.repository.OwnerRepository;
import com.deportlink.deportlink.service.OwnerService;
import com.deportlink.deportlink.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerServiceImplementation implements OwnerService {

    private final OwnerMapper ownerMapper;
    private final ClubMapper clubMapper;
    private final OwnerRepository ownerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public OwnerResponseDto register(OwnerRequestDto ownerDto) throws OwnerAlreadyExistsException {
        log.info("Registering owner: email={}, dni={}", ownerDto.getEmail(), ownerDto.getDni());

        OwnerEntity ownerEntity = ownerMapper.toModel(ownerDto);

        if(ownerRepository.findByDni(ownerEntity.getDni()).isPresent()){
            throw new OwnerAlreadyExistsException("El dueño con dni " + ownerEntity.getDni() + " ya se encuentra registrado");
        }

        if(ownerRepository.findByCuil(ownerEntity.getCuil()).isPresent()){
            throw new OwnerAlreadyExistsException("El dueño con número de cuil: " + ownerEntity.getCuil() + " ya se encuentra registrado");
        }

        if(ownerRepository.findByEmail(ownerEntity.getEmail()).isPresent()){
            throw new OwnerAlreadyExistsException("El dueño con email: " + ownerEntity.getEmail() + " ya se encuentra registrado");
        }

        if(!DateUtils.isOfLegalAge(ownerEntity.getDateOfBirth())){
            throw new UnderageException("Para registrar un club debes ser mayor de edad");
        }

        ownerEntity.setRole(Rol.OWNER);
        ownerEntity.setPassword(passwordEncoder.encode(ownerDto.getPassword()));

        ownerRepository.save(ownerEntity);
        log.info("Owner registered successfully: ownerId={}", ownerEntity.getId());

        return ownerMapper.toResponse(ownerEntity);
    }

    @Override
    @Transactional
    public void deleteById(long id){
        log.info("Deleting owner: ownerId={}", id);

        OwnerEntity ownerEntity = getById(id);
        ownerRepository.delete(ownerEntity);
        log.info("Owner deleted successfully: ownerId={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerEntity getById(long id){
        return ownerRepository.findById(id)
                .orElseThrow(() -> new OwnerNotFoundException("El dueño no fue encontrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerResponseDto getByIdResponse(long id){
        OwnerEntity ownerEntity = getById(id);

        return ownerMapper.toResponse(ownerEntity);
    }

    @Override
    @Transactional
    public void update(long id, OwnerRequestDto ownerDto){
        log.info("Updating owner: ownerId={}, email={}, dni={}", id, ownerDto.getEmail(), ownerDto.getDni());

        OwnerEntity ownerEntity = ownerRepository.findById(id)
                .orElseThrow(() -> new OwnerNotFoundException("El dueño no fue encontrado"));

        ownerEntity.setFirstName(ownerDto.getFirstName());
        ownerEntity.setLastName(ownerDto.getLastName());
        ownerEntity.setPhone(ownerDto.getPhone());
        ownerEntity.setDni(ownerDto.getDni());
        ownerEntity.setCuil(ownerDto.getCuil());

        ownerRepository.save(ownerEntity);
        log.info("Owner updated successfully: ownerId={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnerResponseDto> getAll(){

        return ownerRepository.findAll().
                stream().
                map(ownerMapper::toResponse).
                collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClubResponseDto> getAllClubsByOwner(long idOwner){
        OwnerEntity ownerEntity = getById(idOwner);

        List<ClubEntity> clubFromOwner = ownerEntity.getClubs();
        if(clubFromOwner.isEmpty()){
            throw new ClubNotFoundException("La persona no tiene Clubs registrados a su nombre");
        }

        return clubFromOwner.stream()
                .map(clubMapper::toResponse)
                .collect(Collectors.toList());
    }

}
