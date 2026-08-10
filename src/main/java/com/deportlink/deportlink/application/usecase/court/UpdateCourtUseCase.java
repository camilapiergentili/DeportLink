package com.deportlink.deportlink.application.usecase.court;

import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.model.Sport;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.SportRepositoryPort;
import com.deportlink.deportlink.exception.CourtAlreadyExistsException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class UpdateCourtUseCase {

    private final CourtRepositoryPort courtRepository;
    private final SportRepositoryPort sportRepository;

    public Court execute(Long id, String name, Long sportId) {
        Court court = courtRepository.findById(id)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));
        Sport sport = sportRepository.findById(sportId)
                .orElseThrow(() -> new CourtNotFoundException("Deporte no encontrado"));
        boolean sameSport = court.sportId().equals(sportId);
        boolean sameName = court.name().equalsIgnoreCase(name);
        if (!sameSport || !sameName) {
            if (courtRepository.existsByNameAndBranchAndSport(name, court.branchId(), sportId)) {
                throw new CourtAlreadyExistsException("El nombre de la cancha " + name + " ya se encuentra en esta sucursal");
            }
        }
        return courtRepository.save(court.update(name, sportId, sport.name()));
    }
}