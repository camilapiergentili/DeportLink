package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.CourtOccupancyPort;
import com.deportlink.deportlink.enums.OccupancySourceType;
import com.deportlink.deportlink.model.entity.CourtOccupancyEntity;
import com.deportlink.deportlink.persistence.repository.CourtOccupancyRepository;
import com.deportlink.deportlink.persistence.repository.CourtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class CourtOccupancyAdapter implements CourtOccupancyPort {

    private final CourtOccupancyRepository courtOccupancyRepository;
    private final CourtRepository courtRepository;

    @Override
    public boolean existsOccupancy(Long courtId, LocalDate day, LocalTime startTime) {
        return courtOccupancyRepository.existsByCourt_IdAndOccupiedDayAndStartTime(courtId, day, startTime);
    }

    @Override
    public void registerForClassSession(Long classSessionId, Long courtId, LocalDate day, LocalTime startTime) {
        register(OccupancySourceType.CLASS_SESSION, classSessionId, courtId, day, startTime);
    }

    @Override
    public void registerForReservation(Long reservationId, Long courtId, LocalDate day, LocalTime startTime) {
        register(OccupancySourceType.RESERVATION, reservationId, courtId, day, startTime);
    }

    @Override
    public void releaseForReservation(Long reservationId) {
        courtOccupancyRepository.deleteBySourceTypeAndSourceId(OccupancySourceType.RESERVATION, reservationId);
    }

    private void register(OccupancySourceType sourceType, Long sourceId, Long courtId, LocalDate day, LocalTime startTime) {
        CourtOccupancyEntity entity = new CourtOccupancyEntity();
        entity.setCourt(courtRepository.getReferenceById(courtId));
        entity.setOccupiedDay(day);
        entity.setStartTime(startTime);
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        courtOccupancyRepository.save(entity);
    }
}
