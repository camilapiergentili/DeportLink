package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.ReservationRequestDto;
import com.deportlink.deportlink.dto.response.ReservationResponseDto;
import com.deportlink.deportlink.dto.response.TicketResponseDto;
import com.deportlink.deportlink.exception.*;
import com.deportlink.deportlink.factory.ReservationFactory;
import com.deportlink.deportlink.mapper.ReservationMapper;
import com.deportlink.deportlink.model.StatusReservation;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import com.deportlink.deportlink.model.entity.ScheduleEntity;
import com.deportlink.deportlink.persistence.repository.ReservationRepository;
import com.deportlink.deportlink.service.ReservationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    public ReservationResponseDto bookReservation(ReservationRequestDto dto) {

        // 1. Obtener entidades base
        CourtEntity court = courtService.getById(dto.getIdCourt());
        PlayerEntity player = playerService.getById(dto.getIdPlayer());

        // 2. Crear la reserva (factory)
        ReservationEntity reservation = reservationFactory.create(dto, court, player, StatusReservation.RESERVADO);

        // 3. Validar reglas de negocio
        validateReservation(reservation, court);

        // 4. Guardar en DB
        reservationRepository.save(reservation);

        // 5. Calcular precio
        Double total = pricingService.calculate(reservation);

        // 6. Generar ticket
        TicketResponseDto ticket = ticketService.generateTicket(reservation, total);

        // 7. Armar respuesta
        return buildResponse(reservation, ticket);
    }


    @Override
    @Transactional
    public void cancel(long idReservation, long idPlayer) {
        ReservationEntity reservationEntity = getById(idReservation);
        playerService.getById(idPlayer);

        if (reservationEntity.getPlayer().getId() != idPlayer) {
            throw new ReservationNotFoundException("La reservación no pertenece al jugador seleccionado");
        }

        if (reservationEntity.getStatus().equals(StatusReservation.CANCELADO) ||
                reservationEntity.getStatus().equals(StatusReservation.FINALIZADO)) {
            throw new IllegalStateException("La reserva no puede cancelarse");
        }

        boolean isCancel = isBefore12hours(reservationEntity.getDay(), reservationEntity.getStartTime());

        if (isCancel) {
            throw new CancellationTimeExceededException("El turno no puede ser cancelado 12h antes de la reservacion");
        }

        reservationEntity.setStatus(StatusReservation.CANCELADO);
        reservationRepository.save(reservationEntity);
    }

    @Override
    @Transactional
    public ReservationResponseDto update(long idReservation, long idPlayer, LocalDate day, LocalTime time) {
        playerService.getById(idPlayer);
        ReservationEntity reservationEntity = getById(idReservation);

        if (reservationEntity.getPlayer().getId() != idPlayer) {
            throw new ReservationNotFoundException("La reservación no pertenece al jugador seleccionado");
        }

        if (reservationEntity.getStatus().equals(StatusReservation.FINALIZADO)) {
            throw new IllegalStateException("La reserva no puede modificarse");
        }

        if (reservationEntity.getStatus().equals(StatusReservation.CANCELADO)) {
            throw new IllegalStateException("No se puede modificar una reserva cancelada");
        }

        if (reservationEntity.getDay().equals(day) && reservationEntity.getStartTime().equals(time)) {
            throw new IllegalStateException("El nuevo horario es igual al actual");
        }

        validateFutureDate(day);
        validateAvailability(reservationEntity.getCourt().getId(), day, time);

        reservationFactory.applyScheduleAndStatus(
                reservationEntity,
                reservationEntity.getCourt(),
                reservationEntity.getPlayer(),
                day,
                time,
                StatusReservation.REPROGRAMADO
        );

        reservationRepository.save(reservationEntity);

        return reservationMapper.toResponse(reservationEntity);
    }

    //Obtengo todas las reservas de una cancha en particular.
    @Override
    @Transactional(readOnly = true)
    public List<ReservationEntity> getAllForCount(long idCourt) {
        return reservationRepository.findReservationForCountAndDay(idCourt);
    }

    //Obtengo todas las reservas de un jugador
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getByPlayer(long idPlayer) {
        PlayerEntity player = playerService.getById(idPlayer);
        List<ReservationEntity> reservations = new ArrayList<>(player.getReservations());

        if (reservations.isEmpty()) {
            throw new ReservationNotFoundException(player.getLastName() + " no tiene reservaciones");
        }

        return reservations.stream()
                .map(reservationMapper::toResponse)
                .collect(Collectors.toList());
    }

    //Obtengo las reservas de una cancha un dia particular
    @Override
    @Transactional(readOnly = true)
    public List<LocalTime> getByCourtAndDay(long idCourt, LocalDate day) {
        courtService.getById(idCourt);

        List<ReservationEntity> listReservation = reservationRepository.findByCourtAndDay(idCourt, day);

        if (listReservation.isEmpty()) {
            return List.of();
        }

        return listReservation.stream().map(ReservationEntity::getStartTime)
                .collect(Collectors.toList());
    }

    //Buscar reservacion por id
    @Override
    @Transactional(readOnly = true)
    public ReservationEntity getById(long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException("No existe reservación con el id recibido"));
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponseDto getByIdResponse(long id) {
        return reservationMapper.toResponse(getById(id));
    }


    private ReservationResponseDto buildResponse(
            ReservationEntity reservation,
            TicketResponseDto ticket
    ) {
        ReservationResponseDto response = reservationMapper.toResponse(reservation);
        response.setTicket(ticket);
        return response;
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

    private boolean isBefore12hours(LocalDate reservationDay, LocalTime reservationTime){
        LocalDateTime appointmentDateTime = LocalDateTime.of(reservationDay, reservationTime);
        LocalDateTime currentDate = LocalDateTime.now();

        long hoursDifference = ChronoUnit.HOURS.between(currentDate, appointmentDateTime);

        return hoursDifference < 12;
    }

}

