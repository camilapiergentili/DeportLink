package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.ReservationEntity;
import com.deportlink.deportlink.service.PricingService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@AllArgsConstructor
public class PricingServiceImplementation implements PricingService {

    @Override
    public double calculate(ReservationEntity reservation) {
        log.info("Calculating price for reservation: reservationId={}, duration={} minutes", 
                reservation.getId(), reservation.getDuration() != null ? reservation.getDuration().toMinutes() : null);

        try {
            Duration duration = reservation.getDuration();
            double pricePerHour = reservation.getCourt().getPricePerHour();

            if (duration == null || pricePerHour <= 0) {
                throw new IllegalStateException("No se puede calcular el precio: datos incompletos");
            }

            double total = (duration.toMinutes() / 60.0) * pricePerHour;
            log.info("Price calculated successfully: reservationId={}, total={}", reservation.getId(), total);
            return total;
        } catch (Exception e) {
            log.error("Failed to calculate price for reservation {}: {}", reservation.getId(), e.getMessage(), e);
            throw e;
        }
    }

}
