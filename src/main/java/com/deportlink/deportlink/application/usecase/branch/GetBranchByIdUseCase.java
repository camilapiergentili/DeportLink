package com.deportlink.deportlink.application.usecase.branch;

import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetBranchByIdUseCase {

    private final BranchRepositoryPort branchRepository;

    public Branch execute(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("Sucursal no encontrada"));
    }
}