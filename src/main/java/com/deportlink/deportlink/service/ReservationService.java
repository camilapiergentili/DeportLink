package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.ReservationRequestDto;
import com.deportlink.deportlink.dto.response.ReservationResponseDto;
import com.deportlink.deportlink.model.entity.ReservationEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public interface ReservationService {
    ReservationResponseDto book(ReservationRequestDto reservationDto);
    void cancel(long idReservation, long idPlayer);
    ReservationResponseDto update(long idReservation, long idPlayer, LocalDate day, LocalTime time);
    List<ReservationResponseDto> getByPlayer(long idPlayer);
    Set<LocalTime> getByCourtAndDay(long idCourt, LocalDate day);
    ReservationResponseDto getByIdResponse(long id);
}
