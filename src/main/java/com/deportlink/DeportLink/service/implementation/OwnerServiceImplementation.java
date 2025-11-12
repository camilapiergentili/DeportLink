package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.dto.request.OwnerRequestDto;
import com.deportlink.DeportLink.dto.response.ClubResponseDto;
import com.deportlink.DeportLink.dto.response.OwnerResponseDto;
import com.deportlink.DeportLink.exception.ClubNotFoundException;
import com.deportlink.DeportLink.exception.OwnerAlreadyExistsException;
import com.deportlink.DeportLink.exception.UnderageException;
import com.deportlink.DeportLink.exception.UserNotFoundException;
import com.deportlink.DeportLink.mapper.ClubMapper;
import com.deportlink.DeportLink.mapper.OwnerMapper;
import com.deportlink.DeportLink.model.Rol;
import com.deportlink.DeportLink.model.entity.ClubEntity;
import com.deportlink.DeportLink.model.entity.OwnerEntity;
import com.deportlink.DeportLink.persistence.repository.OwnerRepository;
import com.deportlink.DeportLink.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerServiceImplementation implements OwnerService {

    private final OwnerMapper ownerMapper;
    private final ClubMapper clubMapper;
    private final OwnerRepository ownerRepository;


    public OwnerResponseDto register(OwnerRequestDto ownerDto) throws OwnerAlreadyExistsException {

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

        if(!isOfLegalAge(ownerEntity.getDateOfBirth())){
            throw new UnderageException("Para registrar un club debes ser mayor de edad");
        }

        ownerEntity.setRole(Rol.OWNER);

        ownerRepository.save(ownerEntity);

        return ownerMapper.toResponse(ownerEntity);
    }

    public void deleteById(long id){
        OwnerEntity ownerEntity = ownerRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("El usuario no fue encontrado"));

        ownerRepository.delete(ownerEntity);
    }

    public OwnerEntity getById(long id){
        return ownerRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("El usuario no fue encontrado"));
    }

    public OwnerResponseDto getByIdResponse(long id){
        OwnerEntity ownerEntity = getById(id);

        return ownerMapper.toResponse(ownerEntity);
    }

    public void update(long id, OwnerRequestDto ownerDto){

        OwnerEntity ownerEntity = ownerRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("El usuario no fue encontrado"));

        ownerEntity.setFirstName(ownerDto.getFirstName());
        ownerEntity.setLastName(ownerDto.getLastName());
        ownerEntity.setPhone(ownerDto.getPhone());
        ownerEntity.setDni(ownerDto.getDni());
        ownerEntity.setCuil(ownerDto.getCuil());

        ownerRepository.save(ownerEntity);
    }

    public List<OwnerResponseDto> getAll(){

        return ownerRepository.findAll().
                stream().
                map(ownerMapper::toResponse).
                collect(Collectors.toList());
    }

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

    private boolean isOfLegalAge(LocalDate dateOfBirth){
        return Period.between(dateOfBirth, LocalDate.now()).getYears() >= 18;
    }
}
