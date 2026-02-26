package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.ReservationRequestDto;
import com.deportlink.deportlink.dto.response.ReservationResponseDto;
import com.deportlink.deportlink.exception.ReservationEmptyException;
import com.deportlink.deportlink.exception.SlotNotAvailableException;
import com.deportlink.deportlink.mapper.ReservationMapper;
import com.deportlink.deportlink.model.StatusReservation;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import com.deportlink.deportlink.model.entity.ScheduleEntity;
import com.deportlink.deportlink.persistence.repository.ReservationRepository;
import com.deportlink.deportlink.service.ReservationService;
import com.deportlink.deportlink.service.implementation.court.CourtServiceImplementation;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
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

    public List<ReservationEntity> getAllReservationForCount(long idCourt){
        List<ReservationEntity> listReservation = reservationRepository.findReservationForCountAndDay(idCourt);
        if(listReservation.isEmpty()){
            throw new ReservationEmptyException("No existen reservaciones activas");
        }

        return listReservation;
    }

    public List<LocalTime> getForDay(long idCourt, LocalDate day){
        courtService.getById(idCourt);

        List<ReservationEntity> listReservation = reservationRepository.findByCourtAndDay(idCourt,day);

        if(listReservation.isEmpty()){
            return List.of();
        }

        return listReservation.stream().map(ReservationEntity::getStartTime)
                .collect(Collectors.toList());
    }

    //terminar
    public ReservationResponseDto bookReservation(ReservationRequestDto reservationDto){

        CourtEntity courtEntity = courtService.getById(reservationDto.getIdCourt());
        PlayerEntity playerEntity = playerService.getById(reservationDto.getIdPlayer());

        ReservationEntity reservationEntity = reservationMapper.toModel(reservationDto);

        List<LocalTime> appointmentForDay = getForDay(courtEntity.getId(), reservationEntity.getDay());

        if(appointmentForDay.contains(reservationEntity.getStartTime())){
            throw new SlotNotAvailableException("El horario ya esta reservado");
        }

        ScheduleEntity schedule = scheduleService.getEntityByDay(courtEntity.getId(),
                reservationEntity.getDay().getDayOfWeek());

        slotValido(schedule, reservationEntity.getStartTime());

        reservationEntity.setPlayer(playerEntity);
        reservationEntity.setCourt(courtEntity);
        reservationEntity.setStatus(StatusReservation.RESERVADO);
        reservationEntity.setDuration(schedule.getSlotDuration());

        reservationRepository.save(reservationEntity);

        return reservationMapper.toResponse(reservationEntity);
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




}

