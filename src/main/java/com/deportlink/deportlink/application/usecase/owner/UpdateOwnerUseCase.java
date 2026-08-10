package com.deportlink.deportlink.application.usecase.owner;

import com.deportlink.deportlink.domain.model.Owner;
import com.deportlink.deportlink.domain.port.out.OwnerRepositoryPort;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.exception.OwnerNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateOwnerUseCase {

    private final OwnerRepositoryPort ownerRepository;

    @Transactional
    public void execute(Long id, OwnerRequestDto dto) {
        log.info("Updating owner: ownerId={}", id);

        Owner existing = ownerRepository.findById(id)
                .orElseThrow(() -> new OwnerNotFoundException("El dueño no fue encontrado"));

        Owner updated = new Owner(existing.id(), dto.getFirstName(), dto.getLastName(),
                existing.email(), dto.getPhone(), dto.getDni(), dto.getCuil(),
                existing.dateOfBirth(), existing.password());

        ownerRepository.save(updated);
        log.info("Owner updated: ownerId={}", id);
    }
}