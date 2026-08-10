package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.Schedule;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepositoryPort {
    Schedule save(Schedule schedule);
    List<Schedule> saveAll(List<Schedule> schedules);
    Optional<Schedule> findById(Long id);
    List<Schedule> findAllByCourtId(Long courtId);
    Optional<Schedule> findByCourtIdAndDay(Long courtId, DayOfWeek day);
    Optional<Schedule> findByIdAndCourtId(Long id, Long courtId);
    void delete(Long id);
    boolean existsReservationForDay(Long courtId, int mysqlDay);
}