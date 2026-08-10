package com.deportlink.deportlink.application.usecase.sport;

import com.deportlink.deportlink.domain.model.Sport;
import com.deportlink.deportlink.domain.port.out.SportRepositoryPort;
import com.deportlink.deportlink.exception.SportAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateSportUseCase {

    private final SportRepositoryPort sportRepository;

    @Transactional
    public Sport execute(String name) {
        log.info("Creating sport: name={}", name);
        String upperName = name.toUpperCase();
        if (sportRepository.existsByNameIgnoreCase(upperName)) {
            throw new SportAlreadyExistsException("El deporte " + name + " ya se encuentra registrado");
        }
        Sport saved = sportRepository.save(new Sport(null, upperName));
        log.info("Sport created: sportId={}", saved.id());
        return saved;
    }
}