package com.deportlink.deportlink.application.usecase.club;

import com.deportlink.deportlink.application.port.out.OwnerGateway;
import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddOwnerToClubUseCase {

    private final ClubRepositoryPort clubRepository;
    private final OwnerGateway ownerGateway;

    /**
     * Registra un nuevo dueño en el sistema y lo vincula al club en una sola transacción.
     * Si el registro del dueño falla (CUIL/DNI duplicado), el club no se modifica.
     * Si la vinculación falla (ya es dueño), el registro hace rollback.
     */
    @Transactional
    public void execute(Long clubId, OwnerGateway.OwnerCommand command) {
        log.info("Adding owner to club: clubId={}, email={}", clubId, command.email());

        var club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException("El club no se encontró"));

        // El gateway encapsula validaciones de negocio de Owner (mayoría de edad, unicidad)
        Long ownerId = ownerGateway.register(command);

        // club.addOwner() valida que el dueño no esté ya en el club
        clubRepository.save(club.addOwner(ownerId));
        log.info("Owner added to club: clubId={}, ownerId={}", clubId, ownerId);
    }
}