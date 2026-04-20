package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.ReservationRequestDto;
import com.deportlink.deportlink.dto.response.ReservationResponseDto;
import com.deportlink.deportlink.dto.response.TicketResponseDto;
import com.deportlink.deportlink.exception.*;
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
    private final ScheduleServiceImplementacion scheduleService;
    private final TicketServiceImplementation ticketService;

    @Override
    @Transactional
    public ReservationResponseDto bookReservation(ReservationRequestDto reservationDto){

        CourtEntity courtEntity = courtService.getById(reservationDto.getIdCourt());
        PlayerEntity playerEntity = playerService.getById(reservationDto.getIdPlayer());
        ReservationEntity reservationEntity = reservationMapper.toModel(reservationDto);

        validateFutureDate(reservationEntity.getDay());
        validateAvailability(courtEntity.getId(), reservationEntity.getDay(), reservationEntity.getStartTime());

        ScheduleEntity schedule = getAndValidateSchedule(
                courtEntity.getId(),
                reservationEntity.getDay(),
                reservationEntity.getStartTime());

        reservationEntity.setPlayer(playerEntity);
        reservationEntity.setCourt(courtEntity);
        reservationEntity.setStatus(StatusReservation.RESERVADO);
        reservationEntity.setDuration(schedule.getSlotDuration());


        reservationRepository.save(reservationEntity);

        Double total = getTotalPrice(reservationEntity.getDuration(), courtEntity);

        TicketResponseDto ticket = ticketService.generateTicket(reservationEntity, total);

        ReservationResponseDto reservationResponse = reservationMapper.toResponse(reservationEntity);
        reservationResponse.setTicket(ticket);

        return reservationResponse;
    }

    @Override
    @Transactional
    public void cancel(long idReservation, long idPlayer){
        ReservationEntity reservationEntity = getById(idReservation);
        playerService.getById(idPlayer);

        if(reservationEntity.getPlayer().getId() != idPlayer){
            throw new ReservationNotFoundException("La reservación no pertenece al jugador seleccionado");
        }

        if(reservationEntity.getStatus().equals(StatusReservation.CANCELADO) ||
                reservationEntity.getStatus().equals(StatusReservation.FINALIZADO)){
            throw new IllegalStateException("La reserva no puede cancelarse");
        }

        boolean isCancel = isBefore12hours(reservationEntity.getDay(), reservationEntity.getStartTime());

        if(isCancel){
            throw new CancellationTimeExceededException("El turno no puede ser cancelado 12h antes de la reservacion");
        }

        reservationEntity.setStatus(StatusReservation.CANCELADO);
        reservationRepository.save(reservationEntity);
    }

    @Override
    @Transactional
    public ReservationResponseDto change(long idReservation, long idPlayer, LocalDate day, LocalTime time){
        playerService.getById(idPlayer);
        ReservationEntity reservationEntity = getById(idReservation);

        if(reservationEntity.getPlayer().getId() != idPlayer){
            throw new ReservationNotFoundException("La reservación no pertenece al jugador seleccionado");
        }

        if(reservationEntity.getStatus().equals(StatusReservation.FINALIZADO)){
            throw new IllegalStateException("La reserva no puede modificarse");
        }

        if(reservationEntity.getStatus().equals(StatusReservation.CANCELADO)){
            throw new IllegalStateException("No se puede modificar una reserva cancelada");
        }

        if(reservationEntity.getDay().equals(day) && reservationEntity.getStartTime().equals(time)){
            throw new IllegalStateException("El nuevo horario es igual al actual");
        }

        validateFutureDate(day);
        validateAvailability(reservationEntity.getCourt().getId(), day, time);

        ScheduleEntity scheduleEntity = getAndValidateSchedule(
                reservationEntity.getCourt().getId(),
                day,
                time);


        reservationEntity.setDay(day);
        reservationEntity.setStartTime(time);
        reservationEntity.setDuration(scheduleEntity.getSlotDuration());
        reservationEntity.setStatus(StatusReservation.REPROGRAMADO);

        reservationRepository.save(reservationEntity);

        return reservationMapper.toResponse(reservationEntity);
    }

    //Obtengo todas las reservas de una cancha en particular.
    @Override
    @Transactional(readOnly = true)
    public List<ReservationEntity> getAllForCount(long idCourt){
        return reservationRepository.findReservationForCountAndDay(idCourt);
    }

    //Obtengo todas las reservas de un jugador
    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getByPlayer(long idPlayer){
        PlayerEntity player = playerService.getById(idPlayer);
        List<ReservationEntity> reservations = new ArrayList<>(player.getReservations());

        if(reservations.isEmpty()){
            throw new ReservationNotFoundException(player.getLastName() + " no tiene reservaciones");
        }

        return reservations.stream()
                .map(reservationMapper::toResponse)
                .collect(Collectors.toList());
    }

    //Obtengo las reservas de una cancha un dia particular
    @Override
    @Transactional(readOnly = true)
    public List<LocalTime> getByCourtAndDay(long idCourt, LocalDate day){
        courtService.getById(idCourt);

        List<ReservationEntity> listReservation = reservationRepository.findByCourtAndDay(idCourt,day);

        if(listReservation.isEmpty()){
            return List.of();
        }

        return listReservation.stream().map(ReservationEntity::getStartTime)
                .collect(Collectors.toList());
    }

    //Buscar reservacion por id
    @Override
    @Transactional(readOnly = true)
    public ReservationEntity getById(long id){
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException("No existe reservación con el id recibido"));
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponseDto getByIdResponse(long id){
        return reservationMapper.toResponse(getById(id));
    }


    private Double getTotalPrice(Duration duration, CourtEntity court){
        return (duration == null || court.getPricePerHour() <= 0.0) ?
                0.00 : (duration.toMinutes() / 60.0) * court.getPricePerHour();
    }

    private void slotValido(ScheduleEntity schedule, LocalTime time){
        List<LocalTime>  slotsValid = new ArrayList<>();
        LocalTime current = schedule.getOpeningTime();
        long duration = schedule.getSlotDuration().toMinutes();

        while(!current.plusMinutes(duration).isAfter(schedule.getClosingTime())){
            slotsValid.add(current);
            current = current.plusMinutes(duration);
        }

        if(!slotsValid.contains(time)){
            throw new SlotNotAvailableException("El horario no corresponde a un turno válido");
        }
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

    private ScheduleEntity getAndValidateSchedule(long idCourt, LocalDate day, LocalTime time){
        ScheduleEntity schedule = scheduleService.getByDay(idCourt, day.getDayOfWeek());
        slotValido(schedule, time);
        return schedule;
    }

    private boolean isBefore12hours(LocalDate reservationDay, LocalTime reservationTime){
        LocalDateTime appointmentDateTime = LocalDateTime.of(reservationDay, reservationTime);
        LocalDateTime currentDate = LocalDateTime.now();

        long hoursDifference = ChronoUnit.HOURS.between(currentDate, appointmentDateTime);

        return hoursDifference < 12;
    }

}

