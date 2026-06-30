package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.ReservationRequestDto;
import com.deportlink.deportlink.dto.response.ReservationResponseDto;
import com.deportlink.deportlink.dto.response.TicketResponseDto;
import com.deportlink.deportlink.exception.*;
import com.deportlink.deportlink.factory.ReservationFactory;
import com.deportlink.deportlink.mapper.ReservationMapper;
import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import com.deportlink.deportlink.persistence.repository.ReservationRepository;
import com.deportlink.deportlink.service.ReservationService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class ReservationServiceImplementation implements ReservationService {

    private final ReservationMapper reservationMapper;
    private final ReservationRepository reservationRepository;
    private final CourtServiceImplementation courtService;
    private final PlayerServiceImplementation playerService;
    private final TicketServiceImplementation ticketService;
    private final PricingServiceImplementation pricingService;
    private final ReservationFactory reservationFactory;

    @Override
    @Transactional
    public ReservationResponseDto book(ReservationRequestDto dto) {
        log.info("Booking reservation: courtId={}, playerId={}, day={}, startTime={}", 
                dto.getIdCourt(), dto.getIdPlayer(), dto.getDay(), dto.getStartTime());

        try {
            // 1. Obtener entidades base
            CourtEntity court = courtService.getById(dto.getIdCourt());
            PlayerEntity player = playerService.getById(dto.getIdPlayer());

            // 2. Crear la reserva (factory)
            ReservationEntity reservation = reservationFactory.create(court, player, dto.getDay(), dto.getStartTime(), StatusReservation.RESERVADO);

            // 3. Validar reglas de negocio
            validateReservation(reservation, court);

            // 4. Armar respuesta
            ReservationResponseDto response = processAndRespond(reservation);
            log.info("Reservation booked successfully: reservationId={}", response.getId());
            return response;
        } catch (Exception e) {
            log.error("Failed to book reservation: courtId={}, playerId={}: {}", 
                    dto.getIdCourt(), dto.getIdPlayer(), e.getMessage(), e);
            throw e;
        }
    }


    @Override
    @Transactional
    public void cancel(long idReservation, long idPlayer) {
        log.info("Cancelling reservation: reservationId={}, playerId={}", idReservation, idPlayer);

        try {
            // Busco la reserva por id, y la guardo en la variable reservationEntity
            ReservationEntity reservationEntity = getById(idReservation);

            // Valido que el jugador exista
            playerService.getById(idPlayer);

            if (!Objects.equals(reservationEntity.getPlayer().getId(), idPlayer)) {
                log.warn("Reservation {} does not belong to player {}", idReservation, idPlayer);
                throw new ReservationNotFoundException("La reservación no pertenece al jugador seleccionado");
            }

            if (reservationEntity.getStatus().equals(StatusReservation.CANCELADO) ||
                    reservationEntity.getStatus().equals(StatusReservation.FINALIZADO)) {
                log.warn("Cannot cancel reservation {} - status is {}", idReservation, reservationEntity.getStatus());
                throw new IllegalStateException("La reserva no puede cancelarse");
            }

            boolean isCancel = isBefore12hours(reservationEntity.getDay(), reservationEntity.getStartTime());

            if (isCancel) {
                log.warn("Cancellation denied - less than 12 hours before reservation");
                throw new CancellationTimeExceededException("El turno no puede ser cancelado 12h antes de la reservacion");
            }

            reservationEntity.cancel();
            reservationRepository.save(reservationEntity);
            log.info("Reservation {} cancelled successfully by player {}", idReservation, idPlayer);
        } catch (Exception e) {
            log.error("Failed to cancel reservation {}: {}", idReservation, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public ReservationResponseDto update(long idReservation, long idPlayer, LocalDate day, LocalTime time) {

        //Verifico que el Jugador este registrado
        playerService.getById(idPlayer);

        //Verifico que la reserva exista y la obtengo
        ReservationEntity oldReservation = getById(idReservation);

        // Valido que el jugador pueda modificar la reserva y que el nuevo horario esté disponible
        validateUpdatePermissions(oldReservation, idPlayer, day, time);

        //Guardo para el historial la reserva reprogramada
        oldReservation.setStatus(StatusReservation.REPROGRAMADO);
        reservationRepository.save(oldReservation);

        //Creo la nueva reserva
        ReservationEntity newReservation = reservationFactory.create(
                oldReservation.getCourt(),
                oldReservation.getPlayer(),
                day,
                time,
                StatusReservation.RESERVADO
        );

        //Armar respuesta
        return processAndRespond(newReservation);
    }

    //Obtengo todas las reservas de un jugador
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getByPlayer(long idPlayer) {
        PlayerEntity player = playerService.getById(idPlayer);
        List<ReservationEntity> reservations = new ArrayList<>(player.getReservations());

        return reservations.stream()
                .map(reservationMapper::toResponse)
                .collect(Collectors.toList());
    }

    //Obtengo las reservas de una cancha un dia particular
    @Override
    @Transactional(readOnly = true)
    public List<LocalTime> getByCourtAndDay(long idCourt, LocalDate day) {
        courtService.getById(idCourt);

        //Busco los estados que ocupan un slot y los guardo en activeStatus
        List<StatusReservation> activeStatus = Arrays.stream(StatusReservation.values()).filter(StatusReservation::occupiesSlot).toList();

        //Busco las reservaciones que el estado coincida con la lista de activeStatus
        List<ReservationEntity> listReservation = reservationRepository.findActiveByCourtAndDay(idCourt, day, activeStatus);

        // Solo retorno una lista de horarios.
        return listReservation.stream()
                .map(ReservationEntity::getStartTime)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponseDto getByIdResponse(long id) {
        return reservationMapper.toResponse(getById(id));
    }

    //Buscar reservacion por id
    @Transactional(readOnly = true)
    public ReservationEntity getById(long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException("No existe reservación con el id recibido"));
    }


    private ReservationResponseDto buildResponse(
            ReservationEntity reservation,
            TicketResponseDto ticket
    ) {
        ReservationResponseDto response = reservationMapper.toResponse(reservation);
        response.setTicket(ticket);
        return response;
    }


    private boolean isBefore12hours(LocalDate reservationDay, LocalTime reservationTime){
        LocalDateTime appointmentDateTime = LocalDateTime.of(reservationDay, reservationTime);
        long hoursDifference = ChronoUnit.HOURS.between(LocalDateTime.now(), appointmentDateTime);

        return hoursDifference < 12;
    }

    private ReservationResponseDto processAndRespond(ReservationEntity reservation) {
        reservationRepository.save(reservation);
        Double total = pricingService.calculate(reservation);
        TicketResponseDto ticket = ticketService.generateTicket(reservation, total);
        return buildResponse(reservation, ticket);
    }


    private void validateUpdatePermissions(ReservationEntity oldReservation, long idPlayer, LocalDate day, LocalTime time){

        // Verifico que la reserva que quiere modificar coicida con su nombre
        if (!Objects.equals(oldReservation.getPlayer().getId(), idPlayer)) {
            throw new ReservationNotFoundException("La reservación no pertenece al jugador seleccionado");
        }

        // Si tiene estado FINALIZADO, no puede modificarse.
        if (oldReservation.getStatus().equals(StatusReservation.FINALIZADO)) {
            throw new IllegalStateException("La reserva no puede modificarse");
        }

        // Si esta cancelada, tampoco puede modificarse
        if (oldReservation.getStatus().equals(StatusReservation.CANCELADO)) {
            throw new IllegalStateException("No se puede modificar una reserva cancelada");
        }

        //Que el horario a modificar no sea el mismo que ya tenia
        if (oldReservation.getDay().equals(day) && oldReservation.getStartTime().equals(time)) {
            throw new IllegalStateException("El nuevo horario es igual al actual");
        }

        //Validar que el horario nuevo elegido este disponible
        validateFutureDate(day);
        validateAvailability(oldReservation.getCourt().getId(), day, time);

    }

    private void validateReservation(ReservationEntity reservation, CourtEntity court) {

        validateFutureDate(reservation.getDay());

        validateAvailability(
                court.getId(),
                reservation.getDay(),
                reservation.getStartTime()
        );
    }

    private void validateAvailability(long idCourt, LocalDate day, LocalTime time){
        List<LocalTime> appointmentsForDay = getByCourtAndDay(idCourt, day);
        if(appointmentsForDay.contains(time)){
            throw new SlotNotAvailableException("El horario ya esta reservado");
        }
    }

    private void validateFutureDate(LocalDate day){
        if(day.isBefore(LocalDate.now())){
            throw new InvalidTimeRangeException("La fecha seleccionada ya pasó");
        }
    }

}

