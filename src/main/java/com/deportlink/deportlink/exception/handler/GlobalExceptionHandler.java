package com.deportlink.deportlink.exception.handler;

import com.deportlink.deportlink.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            PlayerNotFoundException.class,
            ClubNotFoundException.class,
            BranchNotFoundException.class,
            CourtNotFoundException.class,
            AddressNotFoundException.class,
            ScheduleNotFoundException.class,
            ReservationNotFoundException.class,
            SportNotFoundException.class,
            OwnerNotFoundException.class,
            UserNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFoundException(RuntimeException ex, HttpServletRequest request) {
        log.error("Resource not found: {}", ex.getMessage());

        return buildErrorResponse(HttpStatus.NOT_FOUND,
                "Not Found",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler({
            ClubNotActivedException.class,
            ClubNotApprovedException.class,
            BranchNotActiveException.class,
            BranchNotApprovedException.class,
            OwnerNotBelongsToClubException.class
    })
    public ResponseEntity<ErrorResponse> handleForbiddenException(RuntimeException ex, HttpServletRequest request) {
        log.error("Forbidden: {}", ex.getMessage());

        return buildErrorResponse(HttpStatus.FORBIDDEN,
                "Forbidden",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler({
            PlayerAlreadyExistsException.class,
            ClubAlreadyExistsException.class,
            BranchAlreadyExistsException.class,
            CourtAlreadyExistsException.class,
            ScheduleAlreadyExistsException.class,
            SportAlreadyExistsException.class,
            OwnerAlreadyExistsException.class,
            SlotNotAvailableException.class,
            StatusAlreadyAppliedException.class
    })
    public ResponseEntity<ErrorResponse> handleConflictException(RuntimeException ex, HttpServletRequest request) {
        log.error("Conflict: {}", ex.getMessage());

        return buildErrorResponse(HttpStatus.CONFLICT,
                "Conflict",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler({
            CancellationTimeExceededException.class,
            InvalidTimeRangeException.class,
            NegativePriceException.class,
            ReservationNotUpdateException.class,
            UnderageException.class
    })
    public ResponseEntity<ErrorResponse> handleUnprocessableException(RuntimeException ex, HttpServletRequest request) {
        log.error("Unprocessable entity: {}", ex.getMessage());

        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(InvalidStatusTransitionException ex, HttpServletRequest request) {
        log.error("Invalid status transition: {}", ex.getMessage());

        return buildErrorResponse(HttpStatus.CONFLICT,
                "Conflict",
                ex.getMessage(),
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
