package com.deportlink.deportlink.application.usecase.club;

import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.enums.ClubType;
import com.deportlink.deportlink.exception.ClubAlreadyExistsException;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateClubUseCase {

    private final ClubRepositoryPort clubRepository;

    /**
     * Club.update() decide si el cambio requiere re-verificación — esa regla vive en el AR.
     * El caso de uso solo valida unicidad de CUIT/razón social antes de delegar al dominio.
     */
    @Transactional
    public Club execute(Long id, String name, String legalName, String cuit, ClubType clubType) {
        log.info("Updating club: id={}", id);

        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new ClubNotFoundException("El club no se encontró"));

        if (!club.cuit().equals(cuit)) {
            clubRepository.findByCuit(cuit)
                    .filter(c -> !c.id().equals(id))
                    .ifPresent(c -> {
                        throw new ClubAlreadyExistsException("El CUIT " + cuit + " ya está registrado");
                    });
        }

        if (!club.legalName().equals(legalName)) {
            clubRepository.findByLegalName(legalName)
                    .filter(c -> !c.id().equals(id))
                    .ifPresent(c -> {
                        throw new ClubAlreadyExistsException("La razón social " + legalName + " ya está registrada");
                    });
        }

        Club updated = club.update(name, legalName, cuit, clubType);
        Club saved = clubRepository.save(updated);
        log.info("Club updated: id={}, requiresReview={}", id, !saved.verificationStatus().equals(club.verificationStatus()));
        return saved;
    }
}