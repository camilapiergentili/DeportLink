package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/**
 * Se usa tanto para "el instructor no existe" como para "un INSTRUCTOR intentó crear/consultar
 * en nombre de otro instructor" — mismo criterio anti-IDOR que ReservationNotFoundException en
 * CancelReservationUseCase: no distinguir "no existe" de "no es tuyo" evita filtrar información.
 */
public class InstructorNotFoundException extends BusinessException {
    public InstructorNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
