package com.deportlink.deportlink.application.usecase.court;

import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.PageRequest;
import com.deportlink.deportlink.domain.port.out.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetApprovedCourtsUseCase {

    private final CourtRepositoryPort courtRepository;

    public PageResult<Court> execute(PageRequest pageRequest) {
        return courtRepository.findApprovedPaginated(pageRequest);
    }
}