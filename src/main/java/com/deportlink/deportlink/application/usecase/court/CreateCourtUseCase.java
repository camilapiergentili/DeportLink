package com.deportlink.deportlink.application.usecase.court;

import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.model.Sport;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.SportRepositoryPort;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.CourtAlreadyExistsException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CreateCourtUseCase {

    private final CourtRepositoryPort courtRepository;
    private final BranchRepositoryPort branchRepository;
    private final SportRepositoryPort sportRepository;

    public Court execute(String name, double pricePerHour, Long branchId, Long sportId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BranchNotFoundException("Sucursal no encontrada"));
        if (!branch.isApproved() || !branch.isActive()) {
            throw new BranchNotApprovedException("La sucursal no puede agregar canchas");
        }
        Sport sport = sportRepository.findById(sportId)
                .orElseThrow(() -> new CourtNotFoundException("Deporte no encontrado"));
        if (courtRepository.existsByNameAndBranchAndSport(name, branchId, sportId)) {
            throw new CourtAlreadyExistsException("El nombre de la cancha " + name + " ya se encuentra en esta sucursal");
        }
        return courtRepository.save(Court.create(name, pricePerHour, branchId, sportId, sport.name()));
    }
}