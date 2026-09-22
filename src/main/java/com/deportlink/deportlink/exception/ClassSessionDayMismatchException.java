package com.deportlink.deportlink.exception;

import org.springframework.http.HttpStatus;

/** La fecha solicitada no cae en el dayOfWeek configurado en el ClassSlot. */
public class ClassSessionDayMismatchException extends BusinessException {
    public ClassSessionDayMismatchException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
