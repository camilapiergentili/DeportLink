package com.deportlink.deportlink.application.usecase.branch;

import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetAllBranchesUseCase {

    private final BranchRepositoryPort branchRepository;

    public List<Branch> execute(Long clubId) {
        return branchRepository.findAllByClub(clubId);
    }
}