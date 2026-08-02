package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import com.deportlink.deportlink.exception.OwnerNotFoundException;
import com.deportlink.deportlink.model.entity.ClubEntity;
import com.deportlink.deportlink.model.entity.OwnerEntity;
import com.deportlink.deportlink.persistence.repository.BranchRepository;
import com.deportlink.deportlink.persistence.repository.ClubRepository;
import com.deportlink.deportlink.persistence.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClubRepositoryAdapter implements ClubRepositoryPort {

    private final ClubRepository clubRepository;
    private final OwnerRepository ownerRepository;
    private final BranchRepository branchRepository;

    @Override
    public Club save(Club domain) {
        ClubEntity entity = domain.id() == null
                ? buildNewEntity(domain)
                : updateExistingEntity(domain);
        return toDomain(clubRepository.save(entity));
    }

    @Override
    public Optional<Club> findById(Long id) {
        return clubRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Club> findByCuit(String cuit) {
        return clubRepository.findByCuit(cuit).map(this::toDomain);
    }

    @Override
    public Optional<Club> findByLegalName(String legalName) {
        return clubRepository.findByLegalName(legalName).map(this::toDomain);
    }

    @Override
    public Page<Club> findByStatus(VerificationStatus vs, ActiveStatus as, Pageable pageable) {
        return clubRepository.findByVerificationStatusAndActiveStatus(vs, as, pageable)
                .map(this::toDomain);
    }

    @Override
    public Page<Club> findAll(Pageable pageable) {
        return clubRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(Long id) {
        clubRepository.deleteById(id);
    }

    @Override
    public boolean hasBranches(Long clubId) {
        return branchRepository.existsByClub_Id(clubId);
    }

    // ─── Entity builders ────────────────────────────────────────────────────────

    private ClubEntity buildNewEntity(Club domain) {
        ClubEntity entity = new ClubEntity();
        applyFields(entity, domain);

        Set<OwnerEntity> owners = loadOwners(domain.ownerIds());
        entity.setOwners(owners);
        // @ManyToMany: OwnerEntity es el lado propietario (tiene @JoinTable).
        // Hay que gestionar ambos lados para que el join table se actualice.
        owners.forEach(owner -> owner.getClubs().add(entity));

        return entity;
    }

    private ClubEntity updateExistingEntity(Club domain) {
        ClubEntity entity = clubRepository.findById(domain.id())
                .orElseThrow(() -> new ClubNotFoundException("Club no encontrado para actualizar"));

        applyFields(entity, domain);

        // Reconciliar el set de dueños comparando IDs actuales vs nuevos
        Set<Long> currentIds = entity.getOwners().stream()
                .map(OwnerEntity::getId).collect(Collectors.toSet());
        Set<Long> newIds = domain.ownerIds();

        // Agregar los que no están
        newIds.stream().filter(id -> !currentIds.contains(id)).forEach(id -> {
            OwnerEntity owner = ownerRepository.findById(id)
                    .orElseThrow(() -> new OwnerNotFoundException("Dueño no encontrado: " + id));
            entity.getOwners().add(owner);
            owner.getClubs().add(entity);
        });

        // Quitar los que ya no deben estar
        Set<OwnerEntity> toRemove = entity.getOwners().stream()
                .filter(o -> !newIds.contains(o.getId()))
                .collect(Collectors.toSet());
        toRemove.forEach(owner -> {
            entity.getOwners().remove(owner);
            owner.getClubs().remove(entity);
        });

        return entity;
    }

    private void applyFields(ClubEntity entity, Club domain) {
        entity.setName(domain.name());
        entity.setLegalName(domain.legalName());
        entity.setCuit(domain.cuit());
        entity.setClubType(domain.clubType());
        entity.setVerificationStatus(domain.verificationStatus());
        entity.setActiveStatus(domain.activeStatus());
    }

    private Set<OwnerEntity> loadOwners(Set<Long> ids) {
        return ids.stream()
                .map(id -> ownerRepository.findById(id)
                        .orElseThrow(() -> new OwnerNotFoundException("Dueño no encontrado: " + id)))
                .collect(Collectors.toSet());
    }

    // ─── Domain ↔ Entity mapping ────────────────────────────────────────────────

    private Club toDomain(ClubEntity entity) {
        // getId() sobre cada proxy de OwnerEntity no dispara lazy load en Hibernate
        Set<Long> ownerIds = entity.getOwners().stream()
                .map(OwnerEntity::getId)
                .collect(Collectors.toSet());

        return new Club(
                entity.getId(),
                entity.getName(),
                entity.getLegalName(),
                entity.getCuit(),
                entity.getClubType(),
                entity.getVerificationStatus(),
                entity.getActiveStatus(),
                ownerIds
        );
    }
}