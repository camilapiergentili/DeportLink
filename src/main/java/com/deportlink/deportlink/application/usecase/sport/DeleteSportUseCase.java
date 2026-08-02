package com.deportlink.deportlink.application.usecase.sport;

import com.deportlink.deportlink.domain.port.out.SportRepositoryPort;
import com.deportlink.deportlink.exception.SportNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteSportUseCase {

    private final SportRepositoryPort sportRepository;

    @Transactional
    public void execute(Long id) {
        log.info("Deleting sport: sportId={}", id);
        sportRepository.findById(id)
                .orElseThrow(() -> new SportNotFoundException("No se encontró el deporte"));
        sportRepository.delete(id);
        log.info("Sport deleted: sportId={}", id);
    }
}