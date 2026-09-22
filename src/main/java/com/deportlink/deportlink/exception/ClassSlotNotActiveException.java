package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/**
 * El ClassSlot está pausado — no se puede crear una ClassSession a partir de él. Mismo status
 * que BranchNotActiveException/ClubNotActivedException (precondición de estado incumplida).
 */
public class ClassSlotNotActiveException extends BusinessException {
    public ClassSlotNotActiveException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
