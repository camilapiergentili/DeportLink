package com.deportlink.deportlink.domain.model;

import java.util.Set;

public record Player(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String password,
        Set<PlayerAddress> addresses
) {}