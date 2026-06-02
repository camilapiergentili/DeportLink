package com.deportlink.deportlink.service;

import com.deportlink.deportlink.model.entity.ReservationEntity;

public interface PricingService {
    double calculate(ReservationEntity reservation);
}
