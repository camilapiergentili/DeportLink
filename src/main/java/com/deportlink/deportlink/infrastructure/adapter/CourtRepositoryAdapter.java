package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.domain.port.out.PageRequest;
import com.deportlink.deportlink.domain.port.out.PageResult;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.persistence.repository.BranchRepository;
import com.deportlink.deportlink.persistence.repository.CourtRepository;
import com.deportlink.deportlink.persistence.repository.SportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CourtRepositoryAdapter implements CourtRepositoryPort {

    private final CourtRepository courtRepository;
    private final BranchRepository branchRepository;
    private final SportRepository sportRepository;

    @Override
    public Court save(Court court) {
        CourtEntity entity = court.id() == null
                ? buildNewEntity(court)
                : updateExistingEntity(court);
        return toDomain(courtRepository.save(entity));
    }

    @Override
    public Optional<Court> findById(Long id) {
        return courtRepository.findByIdWithSport(id).map(this::toDomain);
    }

    @Override
    public void delete(Long id) {
        courtRepository.deleteById(id);
    }

    @Override
    public boolean existsByNameAndBranchAndSport(String name, Long branchId, Long sportId) {
        return courtRepository.existsByNameAndBranch_IdAndSport_Id(name, branchId, sportId);
    }

    @Override
    public List<Court> findActiveByBranch(Long branchId) {
        return courtRepository.findByBranch_IdAndActiveStatus(branchId, ActiveStatus.ACTIVE)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<Court> findActiveByBranchPaginated(Long branchId, PageRequest pageRequest) {
        Pageable pageable = SpringPagingMapper.toSpringPageable(pageRequest);
        return SpringPagingMapper.toPageResult(
                courtRepository.findByBranch_IdAndActiveStatus(branchId, ActiveStatus.ACTIVE, pageable),
                this::toDomain);
    }

    @Override
    public PageResult<Court> findApprovedPaginated(PageRequest pageRequest) {
        Pageable pageable = SpringPagingMapper.toSpringPageable(pageRequest);
        return SpringPagingMapper.toPageResult(
                courtRepository.findApprovedClean(
                        VerificationStatus.APPROVED, ActiveStatus.ACTIVE, ActiveStatus.ACTIVE, pageable
                ), this::toDomain);
    }

    @Override
    public List<Court> findByBranchAndSport(Long branchId, Long sportId) {
        return courtRepository.findByBranch_IdAndSport_Id(branchId, sportId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Court> findAllByBranch(Long branchId) {
        return courtRepository.findByBranchIdWithEagerLoading(branchId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Court> findAll() {
        return courtRepository.findAll()
                .stream().map(this::toDomain).toList();
    }

    private CourtEntity buildNewEntity(Court court) {
        CourtEntity entity = new CourtEntity();
        entity.setName(court.name());
        entity.setPricePerHour(court.pricePerHour());
        entity.setActiveStatus(court.activeStatus());
        entity.setBranch(branchRepository.getReferenceById(court.branchId()));
        entity.setSport(sportRepository.getReferenceById(court.sportId()));
        return entity;
    }

    private CourtEntity updateExistingEntity(Court court) {
        CourtEntity entity = courtRepository.findById(court.id())
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));
        entity.setName(court.name());
        entity.setPricePerHour(court.pricePerHour());
        entity.setActiveStatus(court.activeStatus());
        entity.setSport(sportRepository.getReferenceById(court.sportId()));
        return entity;
    }

    private Court toDomain(CourtEntity entity) {
        return new Court(
                entity.getId(),
                entity.getName(),
                entity.getPricePerHour(),
                entity.getBranch().getId(),
                entity.getSport().getId(),
                entity.getSport().getNameSport(),
                entity.getActiveStatus()
        );
    }
}