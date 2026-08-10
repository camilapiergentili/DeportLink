package com.deportlink.deportlink.application.usecase.club;

import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RejectClubUseCase {

    private final ClubRepositoryPort clubRepository;

    @Transactional
    public void execute(Long id) {
        log.info("Rejecting club: id={}", id);
        var club = clubRepository.findById(id)
                .orElseThrow(() -> new ClubNotFoundException("El club no se encontró"));
        clubRepository.save(club.reject());
        log.info("Club rejected: id={}", id);
    }
}