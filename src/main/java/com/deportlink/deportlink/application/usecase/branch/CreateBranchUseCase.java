package com.deportlink.deportlink.application.usecase.branch;

import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.exception.BranchAlreadyExistsException;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import com.deportlink.deportlink.exception.ClubNotApprovedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CreateBranchUseCase {

    private final BranchRepositoryPort branchRepository;
    private final ClubRepositoryPort clubRepository;

    public Branch execute(String name, Address address, Long clubId, int cancellationWindowHours) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException("Club no encontrado"));
        if (!club.isApproved()) {
            throw new ClubNotApprovedException("El club " + club.legalName() + " aún no está autorizado para agregar sucursales");
        }
        if (branchRepository.existsByNameIgnoreCaseAndClub(name, clubId)) {
            throw new BranchAlreadyExistsException("Ya existe una sucursal con el nombre " + name);
        }
        if (branchRepository.existsByAddressAndClub(address, clubId)) {
            throw new BranchAlreadyExistsException("Ya existe una sucursal en esta dirección");
        }
        return branchRepository.save(Branch.create(name, address, clubId, cancellationWindowHours));
    }
}