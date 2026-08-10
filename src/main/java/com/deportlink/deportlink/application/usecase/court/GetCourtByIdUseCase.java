package com.deportlink.deportlink.application.usecase.court;

import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetCourtByIdUseCase {

    private final CourtRepositoryPort courtRepository;

    public Court execute(Long id) {
        return courtRepository.findById(id)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));
    }
}