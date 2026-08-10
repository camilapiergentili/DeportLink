package com.deportlink.deportlink.application.usecase.owner;

import com.deportlink.deportlink.domain.port.out.OwnerRepositoryPort;
import com.deportlink.deportlink.exception.OwnerNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteOwnerUseCase {

    private final OwnerRepositoryPort ownerRepository;

    @Transactional
    public void execute(Long id) {
        log.info("Deleting owner: ownerId={}", id);
        ownerRepository.findById(id)
                .orElseThrow(() -> new OwnerNotFoundException("El dueño no fue encontrado"));
        ownerRepository.delete(id);
        log.info("Owner deleted: ownerId={}", id);
    }
}