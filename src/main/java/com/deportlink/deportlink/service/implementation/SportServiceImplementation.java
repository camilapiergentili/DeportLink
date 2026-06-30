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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class SportServiceImplementation implements SportService {

    private final SportRepository sportRepository;
    private final SportMapper sportMapper;

    @Override
    @Transactional
    public SportResponseDto create(SportRequestDto sportDto){
        log.info("Creating sport: name={}", sportDto.getNameSport());

        try {
            SportEntity sportEntity = sportMapper.toModel(sportDto);

            boolean exists = sportRepository.findByNameSport(sportEntity.getNameSport().toUpperCase()).isPresent();

            if(exists){
                throw new SportAlreadyExistsException("El deporte " + sportEntity.getNameSport() + " ya se encuentra registrado");
            }

            sportRepository.save(sportEntity);
            log.info("Sport created successfully: sportId={}", sportEntity.getId());

            return sportMapper.toResponse(sportEntity);
        } catch (Exception e) {
            log.error("Failed to create sport: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public SportEntity getById(long id){
        return sportRepository.findById(id)
                .orElseThrow(() -> new SportNotFoundException("No se encontro el deporte"));
    }

    @Override
    @Transactional(readOnly = true)
    public SportResponseDto getByIdResponse(long id) {
        SportEntity sportEntity = getById(id);
        return sportMapper.toResponse(sportEntity);
    }

    @Override
    @Transactional
    public void delete(long id){
        log.info("Deleting sport: sportId={}", id);

        try {
            SportEntity sportEntity = getById(id);
            sportRepository.delete(sportEntity);
            log.info("Sport deleted successfully: sportId={}", id);
        } catch (Exception e) {
            log.error("Failed to delete sport {}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
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
