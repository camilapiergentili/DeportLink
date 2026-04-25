package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@AllArgsConstructor
public class PricingServiceImplementation {

    public Double calculate(ReservationEntity reservation) {

        double hours = reservation.getDuration().toMinutes() / 60.0;
        return hours * reservation.getCourt().getPricePerHour();
    }

    public Double getTotalPrice(Duration duration, CourtEntity court){
        return (duration == null || court.getPricePerHour() <= 0.0) ?
                0.00 : (duration.toMinutes() / 60.0) * court.getPricePerHour();
    }
}
