package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;

import java.util.List;
import java.util.Optional;

public interface BranchRepositoryPort {

    Branch save(Branch branch);

    Optional<Branch> findById(Long id);

    List<Branch> findApprovedByClub(Long clubId);

    List<Branch> findAllByClub(Long clubId);

    void delete(Long id);

    boolean existsByNameIgnoreCaseAndClub(String name, Long clubId);

    boolean existsByAddressAndClub(Address address, Long clubId);

    List<Branch> searchApprovedByName(String name);

    List<Branch> findApprovedBySport(Long sportId);

    List<Branch> findNearby(double lat, double lng, double radiusKm);
}