package com.deportlink.deportlink.domain.model;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.InvalidCancellationWindowException;
import com.deportlink.deportlink.exception.InvalidStatusTransitionException;
import com.deportlink.deportlink.exception.StatusAlreadyAppliedException;

public record Branch(
        Long id,
        String name,
        Address address,
        Long clubId,
        VerificationStatus verificationStatus,
        ActiveStatus activeStatus,
        int cancellationWindowHours
) {
    public Branch {
        // Invariante de dominio — defensa en profundidad además de @NotNull/@Positive en
        // BranchRequestDto. No hay valor por defecto acá a propósito: toda sucursal nueva
        // debe traer el suyo explícitamente (ver CreateBranchUseCase).
        if (cancellationWindowHours <= 0) {
            throw new InvalidCancellationWindowException(
                    "La ventana de cancelación debe ser mayor a cero");
        }
    }

    public static Branch create(String name, Address address, Long clubId, int cancellationWindowHours) {
        return new Branch(null, name, address, clubId, VerificationStatus.PENDING, ActiveStatus.INACTIVE,
                cancellationWindowHours);
    }

    public Branch approve() {
        if (!isPending()) throw new InvalidStatusTransitionException("Solo se pueden aprobar sucursales en estado PENDING");
        return withStatuses(VerificationStatus.APPROVED, ActiveStatus.ACTIVE);
    }

    public Branch reject() {
        if (!isPending()) throw new InvalidStatusTransitionException("Solo se pueden rechazar sucursales en estado PENDING");
        return withStatuses(VerificationStatus.REJECTED, ActiveStatus.INACTIVE);
    }

    public Branch activate() {
        if (!isApproved()) throw new BranchNotApprovedException("La sucursal no se encuentra aprobada");
        if (isActive()) throw new StatusAlreadyAppliedException("La sucursal ya está activa");
        return withStatuses(verificationStatus, ActiveStatus.ACTIVE);
    }

    public Branch deactivate() {
        if (!isApproved()) throw new BranchNotApprovedException("La sucursal no se encuentra aprobada");
        if (!isActive()) throw new StatusAlreadyAppliedException("La sucursal ya está inactiva");
        return withStatuses(verificationStatus, ActiveStatus.INACTIVE);
    }

    public Branch update(String name, Address newAddress, int cancellationWindowHours) {
        if (!this.address.equals(newAddress)) {
            return new Branch(id, name, newAddress, clubId, VerificationStatus.PENDING, ActiveStatus.INACTIVE,
                    cancellationWindowHours);
        }
        return new Branch(id, name, newAddress, clubId, verificationStatus, activeStatus, cancellationWindowHours);
    }

    public boolean isPending() { return verificationStatus == VerificationStatus.PENDING; }
    public boolean isApproved() { return verificationStatus == VerificationStatus.APPROVED; }
    public boolean isActive() { return activeStatus == ActiveStatus.ACTIVE; }

    public Branch withId(Long id) {
        return new Branch(id, name, address, clubId, verificationStatus, activeStatus, cancellationWindowHours);
    }

    private Branch withStatuses(VerificationStatus vs, ActiveStatus as) {
        return new Branch(id, name, address, clubId, vs, as, cancellationWindowHours);
    }
}