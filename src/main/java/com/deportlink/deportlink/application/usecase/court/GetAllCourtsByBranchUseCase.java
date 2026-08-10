package com.deportlink.deportlink.application.usecase.court;

import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetAllCourtsByBranchUseCase {

    private final CourtRepositoryPort courtRepository;

    public List<Court> execute(Long branchId) {
        return courtRepository.findAllByBranch(branchId);
    }
}