package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.reservation.BookReservationUseCase;
import com.deportlink.deportlink.application.usecase.reservation.CancelReservationUseCase;
import com.deportlink.deportlink.application.usecase.reservation.GetAvailableSlotsUseCase;
import com.deportlink.deportlink.application.usecase.reservation.GetPlayerReservationsUseCase;
import com.deportlink.deportlink.application.usecase.reservation.RescheduleReservationUseCase;
import com.deportlink.deportlink.mapper.dto.ReservationMapper;
import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.dto.request.RescheduleRequestDto;
import com.deportlink.deportlink.dto.request.ReservationRequestDto;
import com.deportlink.deportlink.dto.response.ReservationResponseDto;
import com.deportlink.deportlink.security.resolver.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final BookReservationUseCase bookReservationUseCase;
    private final CancelReservationUseCase cancelReservationUseCase;
    private final RescheduleReservationUseCase rescheduleReservationUseCase;
    private final GetAvailableSlotsUseCase getAvailableSlotsUseCase;
    private final GetPlayerReservationsUseCase getPlayerReservationsUseCase;
    private final ReservationMapper reservationMapper;

    @PostMapping
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<ReservationResponseDto> book(
            @RequestBody @Valid ReservationRequestDto dto,
            @CurrentUserId Long userId) {
        Reservation reservation = bookReservationUseCase.execute(
                dto.getIdCourt(), userId, dto.getDay(), dto.getStartTime()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationMapper.toResponse(reservation));
    }

    @DeleteMapping("/{reservationId}")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<ReservationResponseDto> cancel(
            @PathVariable Long reservationId,
            @CurrentUserId Long userId) {
        Reservation cancelled = cancelReservationUseCase.execute(reservationId, userId);
        return ResponseEntity.ok(reservationMapper.toResponse(cancelled));
    }

    @PutMapping("/{reservationId}/reschedule")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<ReservationResponseDto> reschedule(
            @PathVariable Long reservationId,
            @RequestBody @Valid RescheduleRequestDto dto,
            @CurrentUserId Long userId) {
        Reservation rescheduled = rescheduleReservationUseCase.execute(
                reservationId, userId, dto.getNewDay(), dto.getNewStartTime()
        );
        return ResponseEntity.ok(reservationMapper.toResponse(rescheduled));
    }

    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LocalTime>> getAvailableSlots(
            @RequestParam Long courtId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ResponseEntity.ok(getAvailableSlotsUseCase.execute(courtId, day));
    }

    @GetMapping("/player/{playerId}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('PLAYER') and #playerId == principal.id)")
    public ResponseEntity<List<ReservationResponseDto>> getByPlayer(@PathVariable Long playerId) {
        List<ReservationResponseDto> response = getPlayerReservationsUseCase.execute(playerId)
                .stream()
                .map(reservationMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }
}