package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.ScheduleRequestDto;
import com.deportlink.deportlink.dto.response.ScheduleResponseDto;
import com.deportlink.deportlink.exception.*;
import com.deportlink.deportlink.mapper.ScheduleMapper;
import com.deportlink.deportlink.model.ActiveStatus;
import com.deportlink.deportlink.model.VerificationStatus;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import com.deportlink.deportlink.model.entity.ScheduleEntity;
import com.deportlink.deportlink.persistence.repository.ScheduleRepository;
import com.deportlink.deportlink.service.ScheduleService;
import com.deportlink.deportlink.service.implementation.court.CourtServiceImplementation;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class ScheduleServiceImplementacion implements ScheduleService {

    private final CourtServiceImplementation courtService;
    private final ReservationServiceImplementation reservationService;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleRepository scheduleRepository;


    public void addSchedule(long idCourt, List<ScheduleRequestDto> schedulesDto){

        //Busco en la base de datos que la cancha exista
        CourtEntity courtEntity = courtService.getById(idCourt);

        if(!courtEntity.getActiveStatus().equals(ActiveStatus.ACTIVE)){
            throw new ClubNotActivedException("No puede agregar agenda, porque la cancha no se encuentra activa");
        }

        if(!courtEntity.getBranch().getVerificationStatus().equals(VerificationStatus.APPROVED)){
            throw new ClubNotApprovedException("No puede agregar agenda, porque la cancha no se encuentra aprobada");
        }

        // Mappeo la lista que el usuario envio como DTO a ENTITY
        List<ScheduleEntity> scheduleEntityList = scheduleMapper.toModelList(schedulesDto);

        //Valido que el horario de apertura no sea posterior al horario de cierra
        rangeTimeValid(scheduleEntityList);

        //Valido que los horarios que me envia el usuario no coincidan con horarios anteriores de esa cancha.
        List<ScheduleEntity> uniqueSchedule = validateAndFilterSchedules(scheduleEntityList, courtEntity);

        // Obtengo la agenda de cada cancha
        Set<ScheduleEntity> scheduleSet = courtEntity.getSchedules();

        // a la lista de agenda de cada cancha, le setteo la nueva agenda
        scheduleSet.addAll(uniqueSchedule);

        //Por cada agenda se le settea la cancha
        for(ScheduleEntity s : scheduleSet){
            s.setCourt(courtEntity);
        }

        scheduleRepository.saveAll(scheduleSet);
    }


    public void deleteSchedule(long idSchedule, long idCourt){

        ScheduleEntity scheduleEntity = getScheduleForCourt(idCourt, idSchedule);

        int dayMySql = mapJavaDayToMySQL(scheduleEntity.getDay());

        if(scheduleRepository.existsReservationForDay(idCourt, dayMySql)){
            throw new IllegalArgumentException("No se puede eliminar, ya que existen reservas para ese dia");
        }

        scheduleRepository.delete(scheduleEntity);
    }

    public void updateSchedule(long idSchedule, long idCourt, String openingNew, String closingNew){

        ScheduleEntity scheduleEntity = getScheduleForCourt(idCourt, idSchedule);

        LocalTime openingTime = LocalTime.parse(openingNew);
        LocalTime closingTime = LocalTime.parse(closingNew);

        isValidTimeRange(openingTime, closingTime);

        List<ReservationEntity> reservationEntities = reservationService.getAllReservationForCount(idCourt);
        List<ReservationEntity> reservationForDay = filterReservationPerDay(reservationEntities, scheduleEntity);

        reservationTimeValid(reservationForDay, openingTime, closingTime);

        scheduleEntity.setOpeningTime(openingTime);
        scheduleEntity.setClosingTime(closingTime);

        scheduleRepository.save(scheduleEntity);

    }

    public List<ScheduleResponseDto> getAllByCourt(long idCourt){
        CourtEntity courtEntity = courtService.getById(idCourt);

        List<ScheduleEntity> scheduleEntityList = new ArrayList<>(courtEntity.getSchedules());

        if(scheduleEntityList.isEmpty()){
            throw new ScheduleNotFoundException("La cancha aun no tiene agenda disponible");
        }

        return scheduleEntityList.stream()
                .map(scheduleMapper::toResponse).toList();

    }

    public ScheduleResponseDto getByDay(long idCourt, LocalDate day){

        DayOfWeek dayOfWeek = day.getDayOfWeek();

        CourtEntity courtEntity = courtService.getById(idCourt);
        ScheduleEntity scheduleEntityList = scheduleRepository.findByCourtIdAndDay(courtEntity.getId(), dayOfWeek)
                .orElseThrow(() -> new ScheduleNotFoundException(" No se encontro agenda para el dia seleccionado"));

        return scheduleMapper.toResponse(scheduleEntityList);
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

    private boolean isvalidateTimeRange(LocalTime startTimeExists, LocalTime endTimeExists, LocalTime startTimeNew, LocalTime endTimeNew){
        return !(endTimeExists.isBefore(startTimeNew) || startTimeExists.isAfter(endTimeNew));

    }

    private int mapJavaDayToMySQL(DayOfWeek dayOfWeek) {
        return (dayOfWeek.getValue() % 7) + 1;
    }

    private void rangeTimeValid(List<ScheduleEntity> scheduleEntityList){
        for(ScheduleEntity s : scheduleEntityList){
            isValidTimeRange(s.getOpeningTime(), s.getClosingTime());
        }
    }

    private void isValidTimeRange(LocalTime opening, LocalTime close){
        if(isOpeningTimeBeforeClosingTime(opening, close)){
            throw new InvalidTimeRangeException("El horario de inicio no puede ser posterior al horario de fin");
        }
    }

    private List<ScheduleEntity> validateAndFilterSchedules(List<ScheduleEntity> scheduleEntityList, CourtEntity courtEntity){

        List<ScheduleEntity> uniqueSchedule = scheduleEntityList
                .stream()
                .filter(newSchedule -> courtEntity.getSchedules().stream()
                        .noneMatch(s -> s.getDay().equals(newSchedule.getDay()) &&
                                isvalidateTimeRange(s.getOpeningTime(), s.getClosingTime(),
                                        newSchedule.getOpeningTime(), newSchedule.getClosingTime()))
                ).toList();

        if(uniqueSchedule.isEmpty()){
            throw new ScheduleAlreadyExistsException("Los horarios ya existen para la cancha " + courtEntity.getName());
        }

        return uniqueSchedule;
    }

    private List<ReservationEntity> filterReservationPerDay(List<ReservationEntity> reservationEntities, ScheduleEntity scheduleEntity ){
        return reservationEntities.stream()
                .filter(r -> r.getDay().getDayOfWeek().equals(scheduleEntity.getDay()))
                .toList();
    }

    private void reservationTimeValid(List<ReservationEntity> reservationForDay, LocalTime openingTime, LocalTime closingTime){

        for(ReservationEntity r : reservationForDay){
            LocalTime startReservation = r.getStartTime();
            LocalTime endReservation = r.getEndTime();

            if(startReservation.isBefore(openingTime) || endReservation.isAfter(closingTime)){
                throw new ReservationNotUpdateException("Los horarios reservados no estan dentro del nuevo rango horario");
            }
        }
    }


}
