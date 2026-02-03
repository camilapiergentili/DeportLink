package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.exception.ReservationEmptyException;
import com.deportlink.DeportLink.mapper.ReservationMapper;
import com.deportlink.DeportLink.model.entity.CourtEntity;
import com.deportlink.DeportLink.model.entity.ReservationEntity;
import com.deportlink.DeportLink.persistence.repository.ReservationRepository;
import com.deportlink.DeportLink.service.ReservationService;
import com.deportlink.DeportLink.service.implementation.court.CourtServiceImplementation;
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

    public List<ReservationEntity> getAllReservationForCount(long idCourt){
        List<ReservationEntity> listReservation = reservationRepository.findReservationForCountAndDay(idCourt);
        if(listReservation.isEmpty()){
            throw new ReservationEmptyException("No existen reservaciones activas");
        }

        return listReservation;
    }

    public List<LocalTime> getByDay(long idCourt, String day){
        CourtEntity courtEntity = courtService.getById(idCourt);
        LocalDate dayOfReservation = LocalDate.parse(day);

        List<ReservationEntity> listReservation = reservationRepository.findByCourtAndDay(idCourt,dayOfReservation);

        if(listReservation.isEmpty()){
            return List.of();
        }

        return listReservation.stream().map(ReservationEntity::getStartTime)
                .collect(Collectors.toList());
    }




}

