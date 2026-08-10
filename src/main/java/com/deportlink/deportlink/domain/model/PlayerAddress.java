package com.deportlink.deportlink.domain.model;

public record PlayerAddress(
        Long id,
        String streetName,
        int number,
        String city,
        String province,
        int postalCode,
        double latitude,
        double longitude,
        boolean isDefault
) {}