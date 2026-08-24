package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;

import java.util.List;
import java.util.Optional;

public interface BranchRepositoryPort {

    Branch save(Branch branch);

    Optional<Branch> findById(Long id);

    /**
     * Adquiere un lock pesimista sobre TODAS las canchas de la sucursal (0 o más filas) — el
     * recurso que BookReservationUseCase realmente disputa, no la fila de branches. Debe ser
     * la PRIMERA lectura de la transacción en los flujos que la usan (ver DeleteBranchUseCase):
     * bloquear la sucursal misma no generaría contención con una reserva confirmándose en
     * paralelo sobre alguna de sus canchas, y cualquier SELECT plano anterior fijaría el
     * snapshot de REPEATABLE READ de MySQL antes del lock (mismo bug que se corrigió en
     * RescheduleReservationUseCase).
     */
    void lockCourtsForUpdate(Long branchId);

    List<Branch> findApprovedByClub(Long clubId);

    List<Branch> findAllByClub(Long clubId);

    void delete(Long id);

    /** True si la sucursal, o alguna de sus canchas, tiene una reserva asociada — bloquea el borrado. */
    boolean hasReservations(Long branchId);

    boolean existsByNameIgnoreCaseAndClub(String name, Long clubId);

    boolean existsByAddressAndClub(Address address, Long clubId);

    List<Branch> searchApprovedByName(String name);

    List<Branch> findApprovedBySport(Long sportId);

    List<Branch> findNearby(double lat, double lng, double radiusKm);
}