package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.exception.ReservationEmptyException;
import com.deportlink.DeportLink.mapper.ReservationMapper;
import com.deportlink.DeportLink.model.entity.ReservationEntity;
import com.deportlink.DeportLink.persistence.repository.ReservationRepository;
import com.deportlink.DeportLink.service.ReservationService;
import com.deportlink.DeportLink.service.implementation.court.CourtServiceImplementation;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


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


}

