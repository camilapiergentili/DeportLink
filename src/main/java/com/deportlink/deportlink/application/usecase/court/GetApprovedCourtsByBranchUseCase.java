package com.deportlink.deportlink.application.usecase.court;

import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.PageRequest;
import com.deportlink.deportlink.domain.port.out.PageResult;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetApprovedCourtsByBranchUseCase {

    private final CourtRepositoryPort courtRepository;
    private final BranchRepositoryPort branchRepository;

    public PageResult<Court> execute(Long branchId, PageRequest pageRequest) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BranchNotFoundException("Sucursal no encontrada"));
        if (!branch.isApproved() || !branch.isActive()) {
            throw new BranchNotApprovedException("La sucursal no está disponible para jugadores");
        }
        return courtRepository.findActiveByBranchPaginated(branchId, pageRequest);
    }
}