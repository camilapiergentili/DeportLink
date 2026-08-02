package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.Sport;
import com.deportlink.deportlink.domain.port.out.SportRepositoryPort;
import com.deportlink.deportlink.persistence.repository.SportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SportRepositoryAdapter implements SportRepositoryPort {

    private final SportRepository sportRepository;

    @Override
    public Optional<Sport> findById(Long id) {
        return sportRepository.findById(id)
                .map(e -> new Sport(e.getId(), e.getNameSport()));
    }
}