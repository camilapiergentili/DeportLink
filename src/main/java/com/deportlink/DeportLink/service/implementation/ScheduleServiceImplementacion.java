package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.dto.request.ScheduleRequestDto;
import com.deportlink.DeportLink.exception.InvalidTimeRangeException;
import com.deportlink.DeportLink.exception.ReservationNotUpdateException;
import com.deportlink.DeportLink.exception.ScheduleAlreadyExistsException;
import com.deportlink.DeportLink.exception.ScheduleNotFoundException;
import com.deportlink.DeportLink.mapper.ScheduleMapper;
import com.deportlink.DeportLink.model.entity.CourtEntity;
import com.deportlink.DeportLink.model.entity.ReservationEntity;
import com.deportlink.DeportLink.model.entity.ScheduleEntity;
import com.deportlink.DeportLink.persistence.repository.ScheduleRepository;
import com.deportlink.DeportLink.service.ScheduleService;
import lombok.AllArgsConstructor;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ScheduleServiceImplementacion implements ScheduleService {

    private CourtServiceImplementation courtService;
    private ReservationServiceImplementation reservationService;
    private ScheduleMapper scheduleMapper;
    private ScheduleRepository scheduleRepository;

    public void addScheduleToCourt(long idCourt, List<ScheduleRequestDto> schedulesDto){
        CourtEntity courtEntity = courtService.getById(idCourt);

        List<ScheduleEntity> scheduleEntityList = scheduleMapper.toModelList(schedulesDto);

        for(ScheduleEntity s : scheduleEntityList){
            if(isOpeningTimeBeforeClosingTime(s.getOpeningTime(), s.getClosingTime())){
                throw new InvalidTimeRangeException("El horario de inicio no puede ser posterior al horario de fin");
            }
        }

        List<ScheduleEntity> uniqueSchedule = scheduleEntityList
                .stream()
                .filter(newSchedule -> courtEntity.getSchedules().stream()
                        .noneMatch(s -> s.getDay().equals(newSchedule.getDay()) &&
                                isTimeRangeValid(s.getOpeningTime(), s.getClosingTime(),
                                        newSchedule.getOpeningTime(), newSchedule.getClosingTime()))
                ).toList();

        if(uniqueSchedule.isEmpty()){
            throw new ScheduleAlreadyExistsException("Los horarios ya existen para la cancha " + courtEntity.getName());
        }

        Set<ScheduleEntity> scheduleSet = courtEntity.getSchedules();

        scheduleSet.addAll(uniqueSchedule);

        for(ScheduleEntity s : scheduleSet){
            s.setCourt(courtEntity);
        }

        scheduleRepository.saveAll(scheduleSet);

    }

    public void deleteSchedule(long idCourt,  long idSchedule){

        ScheduleEntity scheduleEntity = getScheduleForCourt(idCourt, idSchedule);

        int dayMySql = mapJavaDayToMySQL(scheduleEntity.getDay());

        if(scheduleRepository.existsReservationForDay(idCourt, dayMySql)){
            throw new IllegalArgumentException("No se puede eliminar, ya que existen reservas para ese dia");
        }

        scheduleRepository.delete(scheduleEntity);

    }

    public void updateSchedule(long idCourt, long idSchedule, String openingNew, String closingNew){

        ScheduleEntity scheduleEntity = getScheduleForCourt(idCourt, idSchedule);

        LocalTime openingTime = LocalTime.parse(openingNew);
        LocalTime closingTime = LocalTime.parse(closingNew);

        if(isOpeningTimeBeforeClosingTime(openingTime, closingTime)){
            throw new InvalidTimeRangeException("El horario de inicio no puede ser posterior al horario de fin");
        }

        List<ReservationEntity> reservationEntities = reservationService.getAllReservationForCount(idCourt);

        List<ReservationEntity> reservationForDay = reservationEntities.stream()
                .filter(r -> r.getDay().getDayOfWeek().equals(scheduleEntity.getDay()))
                .toList();

        for(ReservationEntity r : reservationForDay){
            LocalTime startReservation = r.getStartTime();
            LocalTime endReservation = r.getEndTime();

            if(startReservation.isBefore(openingTime) || endReservation.isAfter(closingTime)){
                throw new ReservationNotUpdateException("Los horarios reservados no estan dentro del nuevo rango horario");
            }
        }

        scheduleEntity.setOpeningTime(openingTime);
        scheduleEntity.setClosingTime(closingTime);

        scheduleRepository.save(scheduleEntity);

    }

    private boolean isOpeningTimeBeforeClosingTime(LocalTime open, LocalTime close){
        return open.isAfter(close);
    }

    private ScheduleEntity getScheduleForCourt(long idCourt, long idSchedule){
        CourtEntity courtEntity = courtService.getCourtByIdWithSchedule(idCourt);

        return courtEntity.getSchedules().stream()
                .filter(s -> s.getId() == idSchedule)
                .findFirst()
                .orElseThrow(() -> new ScheduleNotFoundException("No se encontro la agenda"));
    }

    private boolean isTimeRangeValid(LocalTime startTimeExists, LocalTime endTimeExists, LocalTime startTimeNew, LocalTime endTimeNew){
        return !(endTimeExists.isBefore(startTimeNew) || startTimeExists.isAfter(endTimeNew));

    }

    private int mapJavaDayToMySQL(DayOfWeek dayOfWeek) {
        return (dayOfWeek.getValue() % 7) + 1;
    }

}
