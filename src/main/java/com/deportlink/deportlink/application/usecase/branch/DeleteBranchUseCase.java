package com.deportlink.deportlink.application.usecase.branch;

import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class DeleteBranchUseCase {

    private final BranchRepositoryPort branchRepository;

    public void execute(Long id) {
        branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("Sucursal no encontrada"));
        branchRepository.delete(id);
    }
}