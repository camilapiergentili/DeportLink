package com.deportlink.deportlink.domain.model;

import java.time.LocalDate;

public record Owner(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        long dni,
        String cuil,
        LocalDate dateOfBirth,
        String password
) {}