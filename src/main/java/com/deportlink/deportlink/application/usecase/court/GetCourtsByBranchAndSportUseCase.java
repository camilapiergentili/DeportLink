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

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetCourtsByBranchAndSportUseCase {

    private final CourtRepositoryPort courtRepository;
    private final BranchRepositoryPort branchRepository;

    public List<Court> execute(Long branchId, Long sportId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BranchNotFoundException("Sucursal no encontrada"));
        if (!branch.isApproved() || !branch.isActive()) {
            throw new BranchNotApprovedException("La sucursal no está disponible");
        }
        List<Court> courts = courtRepository.findByBranchAndSport(branchId, sportId);
        if (courts.isEmpty()) {
            throw new CourtNotFoundException("No se encontraron canchas con los filtros seleccionados");
        }
        return courts;
    }
}