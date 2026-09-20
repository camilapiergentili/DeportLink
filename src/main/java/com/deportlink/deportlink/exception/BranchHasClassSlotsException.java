package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/** Alguna cancha de la sucursal tiene ClassSlot asociados — no se puede eliminar. */
public class BranchHasClassSlotsException extends BusinessException {
    public BranchHasClassSlotsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
