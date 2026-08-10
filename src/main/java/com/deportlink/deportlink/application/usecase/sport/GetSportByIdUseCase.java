package com.deportlink.deportlink.application.usecase.sport;

import com.deportlink.deportlink.domain.model.Sport;
import com.deportlink.deportlink.domain.port.out.SportRepositoryPort;
import com.deportlink.deportlink.exception.SportNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetSportByIdUseCase {

    private final SportRepositoryPort sportRepository;

    @Transactional(readOnly = true)
    public Sport execute(Long id) {
        return sportRepository.findById(id)
                .orElseThrow(() -> new SportNotFoundException("No se encontró el deporte"));
    }
}