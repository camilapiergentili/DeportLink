package com.deportlink.deportlink.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CourtResponseDto {

    private Long id;
    private String name;
    private double pricePerHour;
    private Long branchId;
    private String sport;
    // horarios cargados bajo demanda vía GET /api/schedules/court/{id}
    private Set<ScheduleResponseDto> availabilities;

}
