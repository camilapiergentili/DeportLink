package com.deportlink.deportlink.application.usecase.club;

import com.deportlink.deportlink.application.port.out.OwnerGateway;
import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import com.deportlink.deportlink.exception.OwnerNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoveOwnerFromClubUseCase {

    private final ClubRepositoryPort clubRepository;
    private final OwnerGateway ownerGateway;

    @Transactional
    public void execute(Long clubId, Long ownerId) {
        log.info("Removing owner from club: clubId={}, ownerId={}", clubId, ownerId);

        var club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException("El club no se encontró"));

        ownerGateway.findById(ownerId)
                .orElseThrow(() -> new OwnerNotFoundException("El dueño no fue encontrado"));

        // club.removeOwner() valida que el dueño pertenezca al club
        clubRepository.save(club.removeOwner(ownerId));
        log.info("Owner removed from club: clubId={}, ownerId={}", clubId, ownerId);
    }
}