package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ClubRepositoryPort {

    Club save(Club club);

    Optional<Club> findById(Long id);

    Optional<Club> findByCuit(String cuit);

    Optional<Club> findByLegalName(String legalName);

    /** Filtra por estado de verificación y activación — usado para listados públicos y de admin. */
    Page<Club> findByStatus(VerificationStatus vs, ActiveStatus as, Pageable pageable);

    Page<Club> findAll(Pageable pageable);

    void delete(Long id);

    /** Verdadero si el club tiene al menos una sucursal. Impide el borrado del club. */
    boolean hasBranches(Long clubId);

    Page<Club> searchApprovedByName(String name, Pageable pageable);
}