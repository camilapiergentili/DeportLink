package com.deportlink.deportlink.application.usecase.club;

import com.deportlink.deportlink.application.port.out.OwnerGateway;
import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.enums.ClubType;
import com.deportlink.deportlink.exception.ClubAlreadyExistsException;
import com.deportlink.deportlink.exception.OwnerNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateClubUseCase {

    private final ClubRepositoryPort clubRepository;
    private final OwnerGateway ownerGateway;

    @Transactional
    public Club execute(String name, String legalName, String cuit, ClubType clubType, Set<Long> ownerIds) {
        log.info("Creating club: name={}, cuit={}", name, cuit);

        ownerIds.forEach(id -> ownerGateway.findById(id)
                .orElseThrow(() -> new OwnerNotFoundException("Dueño no encontrado: " + id)));

        clubRepository.findByCuit(cuit).ifPresent(c -> {
            throw new ClubAlreadyExistsException("El CUIT " + cuit + " ya está registrado");
        });
        clubRepository.findByLegalName(legalName).ifPresent(c -> {
            throw new ClubAlreadyExistsException("La razón social " + legalName + " ya está registrada");
        });

        Club club = Club.create(name, legalName, cuit, clubType, ownerIds);
        Club saved = clubRepository.save(club);
        log.info("Club created: id={}", saved.id());
        return saved;
    }
}