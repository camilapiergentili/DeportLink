package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.Schedule;
import com.deportlink.deportlink.domain.port.out.ScheduleRepositoryPort;
import com.deportlink.deportlink.model.entity.ScheduleEntity;
import com.deportlink.deportlink.persistence.repository.CourtRepository;
import com.deportlink.deportlink.persistence.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ScheduleRepositoryAdapter implements ScheduleRepositoryPort {

    private final ScheduleRepository scheduleRepository;
    private final CourtRepository courtRepository;

    @Override
    public Schedule save(Schedule schedule) {
        ScheduleEntity entity = schedule.id() != null
                ? scheduleRepository.findById(schedule.id()).orElse(new ScheduleEntity())
                : new ScheduleEntity();
        updateEntity(entity, schedule);
        return toSchedule(scheduleRepository.save(entity));
    }

    @Override
    public List<Schedule> saveAll(List<Schedule> schedules) {
        List<ScheduleEntity> entities = schedules.stream().map(s -> {
            ScheduleEntity entity = new ScheduleEntity();
            updateEntity(entity, s);
            return entity;
        }).toList();
        return scheduleRepository.saveAll(entities).stream().map(this::toSchedule).toList();
    }

    @Override
    public Optional<Schedule> findById(Long id) {
        return scheduleRepository.findById(id).map(this::toSchedule);
    }

    @Override
    public List<Schedule> findAllByCourtId(Long courtId) {
        return scheduleRepository.findAll().stream()
                .filter(s -> s.getCourt() != null && s.getCourt().getId().equals(courtId))
                .map(this::toSchedule)
                .toList();
    }

    @Override
    public Optional<Schedule> findByCourtIdAndDay(Long courtId, DayOfWeek day) {
        return scheduleRepository.findByCourtIdAndDay(courtId, day).map(this::toSchedule);
    }

    @Override
    public Optional<Schedule> findByIdAndCourtId(Long id, Long courtId) {
        return scheduleRepository.findByIdAndCourtId(id, courtId).map(this::toSchedule);
    }

    @Override
    public void delete(Long id) {
        scheduleRepository.deleteById(id);
    }

    @Override
    public boolean existsReservationForDay(Long courtId, int mysqlDay) {
        return scheduleRepository.existsReservationForDay(courtId, mysqlDay);
    }

    private void updateEntity(ScheduleEntity entity, Schedule schedule) {
        entity.setDay(schedule.day());
        entity.setOpeningTime(schedule.openingTime());
        entity.setClosingTime(schedule.closingTime());
        entity.setSlotDuration(schedule.slotDuration());
        entity.setCourt(courtRepository.getReferenceById(schedule.courtId()));
    }

    private Schedule toSchedule(ScheduleEntity e) {
        return new Schedule(
                e.getId(),
                e.getCourt() != null ? e.getCourt().getId() : null,
                e.getDay(),
                e.getOpeningTime(),
                e.getClosingTime(),
                e.getSlotDuration()
        );
    }
}