package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.BranchEntity;
import com.deportlink.deportlink.persistence.repository.BranchRepository;
import com.deportlink.deportlink.persistence.repository.ClubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BranchRepositoryAdapter implements BranchRepositoryPort {

    private final BranchRepository branchRepository;
    private final ClubRepository clubRepository;

    @Override
    public Branch save(Branch branch) {
        BranchEntity entity = branch.id() == null
                ? buildNewEntity(branch)
                : updateExistingEntity(branch);
        return toDomain(branchRepository.save(entity));
    }

    @Override
    public Optional<Branch> findById(Long id) {
        return branchRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Branch> findApprovedByClub(Long clubId) {
        return branchRepository
                .findActiveAndApprovedByClubId(clubId, ActiveStatus.ACTIVE, VerificationStatus.APPROVED)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Branch> findAllByClub(Long clubId) {
        return branchRepository.findAllByClubId(clubId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(Long id) {
        branchRepository.deleteById(id);
    }

    @Override
    public boolean existsByNameIgnoreCaseAndClub(String name, Long clubId) {
        return branchRepository.existsByNameIgnoreCaseAndClub_Id(name, clubId);
    }

    @Override
    public boolean existsByAddressAndClub(Address address, Long clubId) {
        return branchRepository.existsByAddressFieldsAndClubId(
                clubId,
                address.streetName(),
                address.number(),
                address.city(),
                address.province(),
                address.postalCode()
        );
    }

    @Override
    public List<Branch> searchApprovedByName(String name) {
        return branchRepository.findByNameContainingIgnoreCaseAndVerificationStatusAndActiveStatus(
                name, VerificationStatus.APPROVED, ActiveStatus.ACTIVE
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Branch> findApprovedBySport(Long sportId) {
        return branchRepository.findApprovedBySport(
                sportId, VerificationStatus.APPROVED, ActiveStatus.ACTIVE, ActiveStatus.ACTIVE
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Branch> findNearby(double lat, double lng, double radiusKm) {
        return branchRepository.findNearby(lat, lng, radiusKm)
                .stream().map(this::toDomain).toList();
    }

    private BranchEntity buildNewEntity(Branch branch) {
        BranchEntity entity = new BranchEntity();
        entity.setName(branch.name());
        entity.setAddress(toAddressEntity(branch.address()));
        entity.setClub(clubRepository.getReferenceById(branch.clubId()));
        entity.setVerificationStatus(branch.verificationStatus());
        entity.setActiveStatus(branch.activeStatus());
        return entity;
    }

    private BranchEntity updateExistingEntity(Branch branch) {
        BranchEntity entity = branchRepository.findById(branch.id())
                .orElseThrow(() -> new BranchNotFoundException("Sucursal no encontrada"));
        entity.setName(branch.name());
        entity.setVerificationStatus(branch.verificationStatus());
        entity.setActiveStatus(branch.activeStatus());
        // Update address in-place to preserve the AddressEntity row
        AddressEntity addr = entity.getAddress();
        addr.setStreetName(branch.address().streetName());
        addr.setNumber(branch.address().number());
        addr.setCity(branch.address().city());
        addr.setProvince(branch.address().province());
        addr.setPostalCode(branch.address().postalCode());
        addr.setLatitude(branch.address().latitude());
        addr.setLongitude(branch.address().longitude());
        return entity;
    }

    private Branch toDomain(BranchEntity entity) {
        Address address = null;
        if (entity.getAddress() != null) {
            AddressEntity a = entity.getAddress();
            address = new Address(
                    a.getStreetName(), a.getNumber(), a.getCity(),
                    a.getProvince(), a.getPostalCode(), a.getLatitude(), a.getLongitude()
            );
        }
        return new Branch(
                entity.getId(),
                entity.getName(),
                address,
                entity.getClub().getId(),
                entity.getVerificationStatus(),
                entity.getActiveStatus()
        );
    }

    private AddressEntity toAddressEntity(Address address) {
        AddressEntity entity = new AddressEntity();
        entity.setStreetName(address.streetName());
        entity.setNumber(address.number());
        entity.setCity(address.city());
        entity.setProvince(address.province());
        entity.setPostalCode(address.postalCode());
        entity.setLatitude(address.latitude());
        entity.setLongitude(address.longitude());
        return entity;
    }
}