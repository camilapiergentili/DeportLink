package com.deportlink.deportlink.domain.model;

public record Address(
        String streetName,
        int number,
        String city,
        String province,
        int postalCode,
        double latitude,
        double longitude
) {}