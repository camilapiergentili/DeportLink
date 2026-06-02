package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import com.deportlink.deportlink.service.PricingService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@AllArgsConstructor
public class PricingServiceImplementation implements PricingService {

    @Override
    public double calculate(ReservationEntity reservation) {

        Duration duration = reservation.getDuration();
        double pricePerHour = reservation.getCourt().getPricePerHour();

        if (duration == null || pricePerHour <= 0) {
            throw new IllegalStateException("No se puede calcular el precio: datos incompletos");
        }

        return (duration.toMinutes() / 60.0) * pricePerHour;
    }

}
