package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.dto.request.ScheduleRequestDto;
import com.deportlink.DeportLink.exception.InvalidTimeRangeException;
import com.deportlink.DeportLink.exception.ScheduleAlreadyExistsException;
import com.deportlink.DeportLink.mapper.CourtMapper;
import com.deportlink.DeportLink.mapper.ScheduleMapper;
import com.deportlink.DeportLink.model.entity.CourtEntity;
import com.deportlink.DeportLink.model.entity.ScheduleEntity;
import com.deportlink.DeportLink.persistence.repository.ScheduleRepository;
import com.deportlink.DeportLink.service.ScheduleService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class ScheduleServiceImplementacion implements ScheduleService {

    private CourtServiceImplementation courtService;
    private ScheduleMapper scheduleMapper;
    private ScheduleRepository scheduleRepository;

    public void addScheduleToCourt(long idCourt, List<ScheduleRequestDto> schedulesDto){
        CourtEntity courtEntity = courtService.getById(idCourt);

        List<ScheduleEntity> scheduleEntityList = scheduleMapper.toModelList(schedulesDto);

        for(ScheduleEntity s : scheduleEntityList){
            if(s.getOpeningTime().isAfter(s.getClosingTime())){
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

    private boolean isTimeRangeValid(LocalTime startTimeExists, LocalTime endTimeExists, LocalTime startTimeNew, LocalTime endTimeNew){
        return !(endTimeExists.isBefore(startTimeNew) || startTimeExists.isAfter(endTimeNew));

    }

}
