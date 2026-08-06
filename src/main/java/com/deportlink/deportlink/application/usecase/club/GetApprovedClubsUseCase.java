package com.deportlink.deportlink.application.usecase.club;

import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.domain.port.out.PageRequest;
import com.deportlink.deportlink.domain.port.out.PageResult;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetApprovedClubsUseCase {

    private final ClubRepositoryPort clubRepository;

    @Transactional(readOnly = true)
    public PageResult<Club> execute(PageRequest pageRequest) {
        return clubRepository.findByStatus(VerificationStatus.APPROVED, ActiveStatus.ACTIVE, pageRequest);
    }
}