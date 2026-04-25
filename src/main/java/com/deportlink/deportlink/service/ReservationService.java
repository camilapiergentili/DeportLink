package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.ReservationRequestDto;
import com.deportlink.deportlink.dto.response.ReservationResponseDto;
import com.deportlink.deportlink.model.entity.ReservationEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ReservationService {
    ReservationResponseDto bookReservation(ReservationRequestDto reservationDto);
    void cancel(long idReservation, long idPlayer);
    ReservationResponseDto update(long idReservation, long idPlayer, LocalDate day, LocalTime time);
    List<ReservationResponseDto> getByPlayer(long idPlayer);
    List<LocalTime> getByCourtAndDay(long idCourt, LocalDate day);
    ReservationResponseDto getByIdResponse(long id);
    List<ReservationEntity> getAllForCount(long idCourt);
    ReservationEntity getById(long id);

}
