package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.Sport;
import com.deportlink.deportlink.domain.port.out.SportRepositoryPort;
import com.deportlink.deportlink.model.entity.SportEntity;
import com.deportlink.deportlink.persistence.repository.SportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SportRepositoryAdapter implements SportRepositoryPort {

    private final SportRepository sportRepository;

    @Override
    public Optional<Sport> findById(Long id) {
        return sportRepository.findById(id).map(this::toSport);
    }

    @Override
    public Sport save(Sport sport) {
        SportEntity entity = sport.id() != null
                ? sportRepository.findById(sport.id()).orElse(new SportEntity())
                : new SportEntity();
        entity.setNameSport(sport.name());
        return toSport(sportRepository.save(entity));
    }

    @Override
    public List<Sport> findAll() {
        return sportRepository.findAll().stream().map(this::toSport).toList();
    }

    @Override
    public void delete(Long id) {
        sportRepository.deleteById(id);
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return sportRepository.findByNameSport(name.toUpperCase()).isPresent();
    }

    private Sport toSport(SportEntity e) {
        return new Sport(e.getId(), e.getNameSport());
    }
}