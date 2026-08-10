package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.Court;

import java.util.List;
import java.util.Optional;

public interface CourtRepositoryPort {

    Court save(Court court);

    Optional<Court> findById(Long id);

    void delete(Long id);

    boolean existsByNameAndBranchAndSport(String name, Long branchId, Long sportId);

    /** Canchas ACTIVAS de una sucursal (sin filtrar por estado de sucursal — el use case lo valida). */
    List<Court> findActiveByBranch(Long branchId);

    PageResult<Court> findActiveByBranchPaginated(Long branchId, PageRequest pageRequest);

    /** Canchas donde la sucursal es APPROVED+ACTIVE y la cancha es ACTIVE. */
    PageResult<Court> findApprovedPaginated(PageRequest pageRequest);

    List<Court> findByBranchAndSport(Long branchId, Long sportId);

    List<Court> findAllByBranch(Long branchId);

    List<Court> findAll();
}