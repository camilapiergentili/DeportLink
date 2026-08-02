package com.deportlink.deportlink.application.usecase.court;

import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class DeactivateCourtUseCase {

    private final CourtRepositoryPort courtRepository;
    private final BranchRepositoryPort branchRepository;

    public Court execute(Long id) {
        Court court = courtRepository.findById(id)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));
        Branch branch = branchRepository.findById(court.branchId())
                .orElseThrow(() -> new BranchNotFoundException("Sucursal no encontrada"));
        if (!branch.isApproved()) {
            throw new BranchNotApprovedException("La sucursal no se encuentra APROBADA para desactivar canchas");
        }
        if (!branch.isActive()) {
            throw new BranchNotApprovedException("La sucursal no se encuentra ACTIVA para desactivar canchas");
        }
        return courtRepository.save(court.deactivate());
    }
}