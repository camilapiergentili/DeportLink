package com.deportlink.deportlink.application.usecase.branch;

import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetNearbyBranchesUseCase {

    private final BranchRepositoryPort branchRepository;

    @Transactional(readOnly = true)
    public List<Branch> execute(double lat, double lng, double radiusKm) {
        return branchRepository.findNearby(lat, lng, radiusKm);
    }
}