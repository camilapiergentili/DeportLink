package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.mapper.ReservationMapper;
import com.deportlink.DeportLink.persistence.repository.ReservationRepository;
import com.deportlink.DeportLink.service.ReservationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@AllArgsConstructor
public class ReservationServiceImplementation implements ReservationService {

    private final ReservationMapper reservationMapper;
    private final ReservationRepository reservationRepository;
    private final CourtServiceImplementation courtService;

//    public List<LocalTime> getAvailable(long idCancha, LocalDate day, int durationInMinutes){
//
//
//
//    }
}

