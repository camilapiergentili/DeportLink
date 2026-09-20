package com.deportlink.deportlink.domain.model;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.exception.NegativePriceException;
import com.deportlink.deportlink.exception.StatusAlreadyAppliedException;

public record Court(
        Long id,
        String name,
        double pricePerHour,
        Long branchId,
        Long sportId,
        String sportName,
        ActiveStatus activeStatus
) {
    public static Court create(String name, double pricePerHour,
                               Long branchId, Long sportId, String sportName) {
        return new Court(null, name, pricePerHour, branchId, sportId, sportName, ActiveStatus.ACTIVE);
    }

    public Court activate() {
        if (isActive()) throw new StatusAlreadyAppliedException("La cancha ya está activa");
        return withStatus(ActiveStatus.ACTIVE);
    }

    public Court deactivate() {
        if (!isActive()) throw new StatusAlreadyAppliedException("La cancha ya está inactiva");
        return withStatus(ActiveStatus.INACTIVE);
    }

    public Court updatePrice(double newPrice) {
        if (newPrice <= 0) throw new NegativePriceException("El precio no puede ser negativo o cero");
        return new Court(id, name, newPrice, branchId, sportId, sportName, activeStatus);
    }

    public Court update(String name, Long sportId, String sportName) {
        return new Court(id, name, pricePerHour, branchId, sportId, sportName, activeStatus);
    }

    public Court moveToBranch(Long newBranchId) {
        return new Court(id, name, pricePerHour, newBranchId, sportId, sportName, activeStatus);
    }

    public boolean isActive() { return activeStatus == ActiveStatus.ACTIVE; }

    public Court withId(Long id) {
        return new Court(id, name, pricePerHour, branchId, sportId, sportName, activeStatus);
    }

    private Court withStatus(ActiveStatus status) {
        return new Court(id, name, pricePerHour, branchId, sportId, sportName, status);
    }
}