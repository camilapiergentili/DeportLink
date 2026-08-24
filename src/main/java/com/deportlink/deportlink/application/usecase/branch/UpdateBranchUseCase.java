package com.deportlink.deportlink.application.usecase.branch;

import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.exception.BranchAlreadyExistsException;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class UpdateBranchUseCase {

    private final BranchRepositoryPort branchRepository;

    public Branch execute(Long id, String name, Address newAddress, int cancellationWindowHours) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("Sucursal no encontrada"));
        if (!branch.name().equalsIgnoreCase(name)
                && branchRepository.existsByNameIgnoreCaseAndClub(name, branch.clubId())) {
            throw new BranchAlreadyExistsException("Ya existe una sucursal con el nombre " + name);
        }
        if (!branch.address().equals(newAddress)
                && branchRepository.existsByAddressAndClub(newAddress, branch.clubId())) {
            throw new BranchAlreadyExistsException("Ya existe una sucursal en esta dirección");
        }
        return branchRepository.save(branch.update(name, newAddress, cancellationWindowHours));
    }
}