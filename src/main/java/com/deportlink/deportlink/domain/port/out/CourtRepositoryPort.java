package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.Court;

import java.util.List;
import java.util.Optional;

public interface CourtRepositoryPort {

    Court save(Court court);

    Optional<Court> findById(Long id);

    /**
     * Igual que findById, pero adquiere un lock pesimista sobre la cancha. Debe ser la
     * PRIMERA lectura de la transacción en los flujos que la usan (ver DeleteCourtUseCase) —
     * si no, el snapshot de REPEATABLE READ de MySQL queda fijado antes del lock, y un chequeo
     * posterior (hasReservations) puede seguir viendo datos anteriores al commit de una
     * reserva confirmándose en paralelo. Mismo mecanismo que BookReservationUseCase.
     */
    Optional<Court> findByIdForUpdate(Long id);

    void delete(Long id);

    /** True si la cancha tiene alguna reserva asociada (cualquier estado) — bloquea el borrado. */
    boolean hasReservations(Long courtId);

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