package com.deportlink.deportlink.application.usecase.court;

import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetApprovedCourtsUseCase {

    private final CourtRepositoryPort courtRepository;

    public Page<Court> execute(Pageable pageable) {
        return courtRepository.findApprovedPaginated(pageable);
    }
}