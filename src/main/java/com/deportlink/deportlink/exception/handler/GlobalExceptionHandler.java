package com.deportlink.deportlink.exception.handler;

import com.deportlink.deportlink.exception.BusinessException;
import com.deportlink.deportlink.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Único punto de traducción para TODAS las excepciones de negocio (~35 y creciendo): cada
    // una declara su propio HttpStatus en su constructor (ver BusinessException). Agregar una
    // excepción de negocio nueva no requiere tocar esta clase — principio abierto/cerrado. Antes
    // había que sumarla a mano a una de varias listas @ExceptionHandler({...}); si alguien se
    // olvidaba, la excepción caía silenciosamente en handleGenericException con 500 (pasó con
    // ClubHasBranchesException y ScheduleHasReservationsException).
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.error("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());

        return buildErrorResponse(ex.getStatus(),
                ex.getStatus().getReasonPhrase(),
                ex.getMessage(),
                request);
    }

    // Una violación de integridad (FK, unique) en este dominio siempre refleja un conflicto de
    // negocio conocido (p. ej. una reserva creada justo entre el chequeo y el delete de una
    // sucursal/cancha/club/horario) — nunca un error interno. Se traduce a 409 en vez de dejarla
    // caer en el handler genérico de 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Data integrity violation: {}", ex.getMessage());

        return buildErrorResponse(HttpStatus.CONFLICT,
                "Conflict",
                "No se pudo completar la operación porque el recurso tiene datos asociados",
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.error("Validation failed: {}", ex.getMessage());

        Map<String, String> validationErrors = new LinkedHashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(fieldError -> validationErrors.put(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ));

        return buildErrorResponse(HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Error de validación: " + validationErrors.toString(),
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden",
                "No tenés permisos para acceder a este recurso", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing required parameter: {}", ex.getParameterName());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Failed login attempt from {}", request.getRemoteAddr());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", "Credenciales inválidas", request);
    }

    // TooManyRequestsException también es una BusinessException, pero Spring resuelve el
    // handler más específico de la jerarquía — este gana sobre handleBusinessException. Se
    // mantiene aparte porque loguea la IP bloqueada, algo que ninguna otra excepción necesita.
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        log.warn("IP bloqueada por exceso de intentos: {}", request.getRemoteAddr());
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage(), request);
    }

    // Red de seguridad final: a partir de este refactor, ya no debería recibir excepciones de
    // negocio (todas extienden BusinessException y las captura el handler de arriba) — solo
    // errores de programación reales, no anticipados.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "Ocurrió un error inesperado",
                request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request
    ) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .method(request.getMethod())
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }
}
