package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.dto.request.SportRequestDto;
import com.deportlink.DeportLink.dto.response.SportResponseDto;
import com.deportlink.DeportLink.exception.SportAlreadyExistsException;
import com.deportlink.DeportLink.exception.SportNotFoundException;
import com.deportlink.DeportLink.mapper.SportMapper;
import com.deportlink.DeportLink.model.entity.SportEntity;
import com.deportlink.DeportLink.persistence.repository.SportRepository;
import com.deportlink.DeportLink.service.SportService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SportServiceImplementation implements SportService {

    private final SportRepository sportRepository;
    private final SportMapper sportMapper;

    public void create(SportRequestDto sportDto){

        SportEntity sportEntity = sportMapper.toModel(sportDto);

        boolean exists = sportRepository.findByNameSport(sportEntity.getNameSport().toUpperCase()).isPresent();

        if(exists){
            throw new SportAlreadyExistsException("El deporte " + sportEntity.getNameSport() + " ya se encuentra registrado");
        }

        sportRepository.save(sportEntity);
    }

    public SportEntity getById(long id){
        return sportRepository.findById(id)
                .orElseThrow(() -> new SportNotFoundException("No se encontro el deporte"));
    }

    public SportResponseDto getByIdResponse(long id) {
        SportEntity sportEntity = getById(id);
        return sportMapper.toResponse(sportEntity);
    }

    public void delete(long id){
        SportEntity sportEntity = getById(id);

        sportRepository.delete(sportEntity);
    }

    public List<SportResponseDto> getAll(){

        List<SportEntity> sportsEntity = sportRepository.findAll();

        if(sportsEntity.isEmpty()){
            throw new SportNotFoundException("No se encontraron deportes registrados");
        }

        return sportsEntity.stream()
                .map(sportMapper::toResponse)
                .collect(Collectors.toList());
    }

}
