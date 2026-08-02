package com.deportlink.deportlink.controller;

import com.deportlink.deportlink.application.usecase.reservation.BookReservationUseCase;
import com.deportlink.deportlink.application.usecase.reservation.CancelReservationUseCase;
import com.deportlink.deportlink.application.usecase.reservation.GetAvailableSlotsUseCase;
import com.deportlink.deportlink.application.usecase.reservation.GetPlayerReservationsUseCase;
import com.deportlink.deportlink.application.usecase.reservation.RescheduleReservationUseCase;
import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.model.Ticket;
import com.deportlink.deportlink.dto.request.RescheduleRequestDto;
import com.deportlink.deportlink.dto.request.ReservationRequestDto;
import com.deportlink.deportlink.dto.response.ReservationResponseDto;
import com.deportlink.deportlink.dto.response.TicketResponseDto;
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

    /**
     * Reserva un turno. Solo jugadores pueden reservar canchas.
     * El playerId en el body es el jugador que reserva — en un sistema maduro
     * se extraería del JWT para evitar que un jugador reserve en nombre de otro.
     */
    @PostMapping
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<ReservationResponseDto> book(@RequestBody @Valid ReservationRequestDto dto) {
        Reservation reservation = bookReservationUseCase.execute(
                dto.getIdCourt(), dto.getIdPlayer(), dto.getDay(), dto.getStartTime()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(reservation));
    }

    /**
     * Cancela una reserva existente. El caso de uso valida ownership y ventana de 12 horas.
     * playerId como query param: en el futuro se leerá del contexto de seguridad.
     */
    @DeleteMapping("/{reservationId}")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<ReservationResponseDto> cancel(
            @PathVariable Long reservationId,
            @RequestParam Long playerId) {
        Reservation cancelled = cancelReservationUseCase.execute(reservationId, playerId);
        return ResponseEntity.ok(toResponse(cancelled));
    }

    /**
     * Reprograma un turno: marca el actual como REPROGRAMADO y crea uno nuevo.
     * Operación atómica — si la creación del nuevo turno falla, el estado anterior
     * hace rollback y el jugador conserva la reserva original.
     */
    @PutMapping("/{reservationId}/reschedule")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<ReservationResponseDto> reschedule(
            @PathVariable Long reservationId,
            @RequestBody @Valid RescheduleRequestDto dto) {
        Reservation rescheduled = rescheduleReservationUseCase.execute(
                reservationId, dto.getPlayerId(), dto.getNewDay(), dto.getNewStartTime()
        );
        return ResponseEntity.ok(toResponse(rescheduled));
    }

    /**
     * Devuelve los horarios disponibles para una cancha en un día.
     * Resultado advisory — puede cambiar entre la consulta y la reserva efectiva.
     * El lock real ocurre en BookReservationUseCase.
     */
    @GetMapping("/available")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LocalTime>> getAvailableSlots(
            @RequestParam Long courtId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return ResponseEntity.ok(getAvailableSlotsUseCase.execute(courtId, day));
    }

    /**
     * Lista todas las reservas de un jugador.
     * Admin puede consultar cualquier jugador; el propio jugador solo las suyas.
     */
    @GetMapping("/player/{playerId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PLAYER')")
    public ResponseEntity<List<ReservationResponseDto>> getByPlayer(@PathVariable Long playerId) {
        List<ReservationResponseDto> response = getPlayerReservationsUseCase.execute(playerId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    // ─── Domain → DTO mapping ────────────────────────────────────────────────────

    private ReservationResponseDto toResponse(Reservation domain) {
        ReservationResponseDto dto = new ReservationResponseDto();
        dto.setId(domain.id());
        dto.setDay(domain.timeSlot().day());
        dto.setStartTime(domain.timeSlot().startTime());
        dto.setDurationMinutes(domain.timeSlot().duration().toMinutes());
        dto.setStatus(domain.status());
        dto.setTicket(domain.ticket() != null ? toTicketResponse(domain.ticket()) : null);
        return dto;
    }

    private TicketResponseDto toTicketResponse(Ticket ticket) {
        TicketResponseDto dto = new TicketResponseDto();
        dto.setId(ticket.id() != null ? ticket.id() : 0L);
        dto.setPlayer(ticket.playerName() + " " + ticket.playerLastName());
        dto.setCourtName(ticket.courtName());
        dto.setSport(ticket.sport());
        dto.setBranchName(ticket.branchName());
        dto.setBranchAddress(ticket.branchAddress());
        dto.setTotalPrice(ticket.totalPrice());
        dto.setIssuedAt(ticket.issuedAt() != null ? ticket.issuedAt().toString() : null);
        return dto;
    }
}