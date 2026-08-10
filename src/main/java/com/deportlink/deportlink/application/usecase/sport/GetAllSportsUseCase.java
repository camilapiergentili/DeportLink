package com.deportlink.deportlink.application.usecase.sport;

import com.deportlink.deportlink.domain.model.Sport;
import com.deportlink.deportlink.domain.port.out.SportRepositoryPort;
import com.deportlink.deportlink.exception.SportNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetAllSportsUseCase {

    private final SportRepositoryPort sportRepository;

    @Transactional(readOnly = true)
    public List<Sport> execute() {
        List<Sport> sports = sportRepository.findAll();
        if (sports.isEmpty()) {
            throw new SportNotFoundException("No se encontraron deportes registrados");
        }
        return sports;
    }
}