package com.deportlink.deportlink.application.usecase.club;

import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.domain.port.out.PageRequest;
import com.deportlink.deportlink.domain.port.out.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetAllClubsUseCase {

    private final ClubRepositoryPort clubRepository;

    @Transactional(readOnly = true)
    public PageResult<Club> execute(PageRequest pageRequest) {
        return clubRepository.findAll(pageRequest);
    }
}