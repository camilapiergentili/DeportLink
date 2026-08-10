package com.deportlink.deportlink.application.usecase.club;

import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteClubUseCase {

    private final ClubRepositoryPort clubRepository;

    @Transactional
    public void execute(Long id) {
        log.info("Deleting club: id={}", id);

        clubRepository.findById(id)
                .orElseThrow(() -> new ClubNotFoundException("El club no se encontró"));

        if (clubRepository.hasBranches(id)) {
            throw new IllegalStateException("No se puede eliminar un club con sucursales activas");
        }

        clubRepository.delete(id);
        log.info("Club deleted: id={}", id);
    }
}