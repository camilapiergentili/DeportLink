package com.deportlink.deportlink.application.usecase.court;

import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class UpdateCourtPriceUseCase {

    private final CourtRepositoryPort courtRepository;

    public Court execute(Long id, double newPrice) {
        Court court = courtRepository.findById(id)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));
        return courtRepository.save(court.updatePrice(newPrice));
    }
}