package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.Sport;

import java.util.List;
import java.util.Optional;

public interface SportRepositoryPort {
    Optional<Sport> findById(Long id);
    Sport save(Sport sport);
    List<Sport> findAll();
    void delete(Long id);
    boolean existsByNameIgnoreCase(String name);
}