package com.deportlink.deportlink.domain.model;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.ClubType;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate Root del bounded context Club.
 * <p>
 * Toda transición de estado (aprobar, rechazar, activar, desactivar) pasa por aquí.
 * La lógica de negocio no vive en ClubServiceImplementation — vive en este record.
 * <p>
 * ownerIds: referencia por ID, no por objeto JPA. El dominio no conoce OwnerEntity.
 */
public record Club(
        Long id,
        String name,
        String legalName,
        String cuit,
        ClubType clubType,
        VerificationStatus verificationStatus,
        ActiveStatus activeStatus,
        Set<Long> ownerIds
) {

    public static Club create(String name, String legalName, String cuit,
                               ClubType clubType, Set<Long> ownerIds) {
        Objects.requireNonNull(name, "El nombre no puede ser nulo");
        Objects.requireNonNull(legalName, "La razón social no puede ser nula");
        Objects.requireNonNull(cuit, "El CUIT no puede ser nulo");
        Objects.requireNonNull(clubType, "El tipo de club no puede ser nulo");

        if (ownerIds == null || ownerIds.isEmpty()) {
            throw new IllegalArgumentException("El club debe estar asociado a al menos un dueño");
        }
        // Nuevo club: siempre arranca pendiente de aprobación e inactivo
        return new Club(null, name, legalName, cuit, clubType,
                VerificationStatus.PENDING, ActiveStatus.INACTIVE, Set.copyOf(ownerIds));
    }

    // ─── Transiciones de estado de verificación ─────────────────────────────────

    public Club approve() {
        if (verificationStatus != VerificationStatus.PENDING) {
            throw new InvalidStatusTransitionException("Solo se pueden aprobar clubs en estado PENDING");
        }
        return withStatuses(VerificationStatus.APPROVED, ActiveStatus.ACTIVE);
    }

    public Club reject() {
        if (verificationStatus != VerificationStatus.PENDING) {
            throw new InvalidStatusTransitionException("Solo se pueden rechazar clubs en estado PENDING");
        }
        return withStatuses(VerificationStatus.REJECTED, ActiveStatus.INACTIVE);
    }

    // ─── Transiciones de estado de activación ───────────────────────────────────

    public Club activate() {
        if (verificationStatus != VerificationStatus.APPROVED) {
            throw new ClubNotApprovedException("El club no se encuentra aprobado");
        }
        if (activeStatus == ActiveStatus.ACTIVE) {
            throw new StatusAlreadyAppliedException("El club ya está activo");
        }
        return withStatuses(verificationStatus, ActiveStatus.ACTIVE);
    }

    public Club deactivate() {
        if (verificationStatus != VerificationStatus.APPROVED) {
            throw new ClubNotApprovedException("El club no se encuentra aprobado");
        }
        if (activeStatus == ActiveStatus.INACTIVE) {
            throw new StatusAlreadyAppliedException("El club ya está inactivo");
        }
        return withStatuses(verificationStatus, ActiveStatus.INACTIVE);
    }

    // ─── Actualización de datos ──────────────────────────────────────────────────

    /**
     * Cambiar razón social, CUIT o tipo legal requiere re-verificación.
     * El dominio aplica esa regla automáticamente — el caso de uso no necesita conocerla.
     */
    public Club update(String newName, String newLegalName, String newCuit, ClubType newClubType) {
        boolean requiresReview = !legalName.equals(newLegalName)
                || !cuit.equals(newCuit)
                || !clubType.equals(newClubType);

        VerificationStatus newVS = requiresReview ? VerificationStatus.PENDING : verificationStatus;
        ActiveStatus newAS = requiresReview ? ActiveStatus.INACTIVE : activeStatus;

        return new Club(id, newName, newLegalName, newCuit, newClubType, newVS, newAS, ownerIds);
    }

    // ─── Gestión de dueños ───────────────────────────────────────────────────────

    public Club addOwner(Long ownerId) {
        if (ownerIds.contains(ownerId)) {
            throw new OwnerAlreadyExistsException("El dueño ya es propietario del club");
        }
        Set<Long> updated = new HashSet<>(ownerIds);
        updated.add(ownerId);
        return new Club(id, name, legalName, cuit, clubType, verificationStatus, activeStatus, Set.copyOf(updated));
    }

    public Club removeOwner(Long ownerId) {
        if (!ownerIds.contains(ownerId)) {
            throw new OwnerNotBelongsToClubException("La persona no pertenece al club");
        }
        Set<Long> updated = new HashSet<>(ownerIds);
        updated.remove(ownerId);
        return new Club(id, name, legalName, cuit, clubType, verificationStatus, activeStatus, Set.copyOf(updated));
    }

    // ─── Queries de dominio ──────────────────────────────────────────────────────

    public boolean isOwnedBy(Long ownerId) {
        return ownerIds.contains(ownerId);
    }

    public boolean isPending() {
        return verificationStatus == VerificationStatus.PENDING;
    }

    public boolean isApproved() {
        return verificationStatus == VerificationStatus.APPROVED;
    }

    public boolean isActive() {
        return activeStatus == ActiveStatus.ACTIVE;
    }

    // ─── Helpers internos ────────────────────────────────────────────────────────

    public Club withId(Long id) {
        return new Club(id, name, legalName, cuit, clubType, verificationStatus, activeStatus, ownerIds);
    }

    private Club withStatuses(VerificationStatus vs, ActiveStatus as) {
        return new Club(id, name, legalName, cuit, clubType, vs, as, ownerIds);
    }
}