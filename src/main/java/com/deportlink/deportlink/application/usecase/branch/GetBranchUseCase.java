package com.deportlink.deportlink.application.usecase.branch;

import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.exception.BranchNotActiveException;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetBranchUseCase {

    private final BranchRepositoryPort branchRepository;

    public Branch execute(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("Sucursal no encontrada"));
        if (!branch.isApproved()) {
            throw new BranchNotApprovedException("La sucursal no se puede mostrar, la documentación está siendo revisada");
        }
        if (!branch.isActive()) {
            throw new BranchNotActiveException("La sucursal se encuentra inactiva");
        }
        return branch;
    }
}