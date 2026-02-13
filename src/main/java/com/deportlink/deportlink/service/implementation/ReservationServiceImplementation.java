package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.ReservationRequestDto;
import com.deportlink.deportlink.exception.ReservationEmptyException;
import com.deportlink.deportlink.mapper.ReservationMapper;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import com.deportlink.deportlink.persistence.repository.ReservationRepository;
import com.deportlink.deportlink.service.ReservationService;
import com.deportlink.deportlink.service.implementation.court.CourtServiceImplementation;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;


@Service
@AllArgsConstructor
public class ReservationServiceImplementation implements ReservationService {

    private final ReservationMapper reservationMapper;
    private final ReservationRepository reservationRepository;
    private final CourtServiceImplementation courtService;
    private final PlayerServiceImplementation playerService;

    public List<ReservationEntity> getAllReservationForCount(long idCourt){
        List<ReservationEntity> listReservation = reservationRepository.findReservationForCountAndDay(idCourt);
        if(listReservation.isEmpty()){
            throw new ReservationEmptyException("No existen reservaciones activas");
        }

        return listReservation;
    }

    public List<LocalTime> getForDay(long idCourt, String day){
        courtService.getById(idCourt);
        LocalDate dayOfReservation = LocalDate.parse(day);

        List<ReservationEntity> listReservation = reservationRepository.findByCourtAndDay(idCourt,dayOfReservation);

        if(listReservation.isEmpty()){
            return List.of();
        }

        return listReservation.stream().map(ReservationEntity::getStartTime)
                .collect(Collectors.toList());
    }

    //terminar
    public void bookReservation(ReservationRequestDto reservationDto){
        CourtEntity courtEntity = courtService.getById(reservationDto.getIdCourt());
        PlayerEntity playerEntity = playerService.getById(reservationDto.getIdPlayer());

        List<LocalTime> appointmentForDay = getForDay(courtEntity.getId(), reservationDto.getDay());

    }




}

