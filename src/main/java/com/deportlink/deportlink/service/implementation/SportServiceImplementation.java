package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.SportRequestDto;
import com.deportlink.deportlink.dto.response.SportResponseDto;
import com.deportlink.deportlink.exception.SportAlreadyExistsException;
import com.deportlink.deportlink.exception.SportNotFoundException;
import com.deportlink.deportlink.mapper.SportMapper;
import com.deportlink.deportlink.model.entity.SportEntity;
import com.deportlink.deportlink.persistence.repository.SportRepository;
import com.deportlink.deportlink.service.SportService;
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
