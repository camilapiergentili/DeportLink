package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.Sport;

import java.util.Optional;

public interface SportRepositoryPort {
    Optional<Sport> findById(Long id);
}