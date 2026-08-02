package com.deportlink.deportlink.application.usecase.owner;

import com.deportlink.deportlink.domain.model.Owner;
import com.deportlink.deportlink.domain.port.out.OwnerRepositoryPort;
import com.deportlink.deportlink.exception.OwnerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetOwnerByIdUseCase {

    private final OwnerRepositoryPort ownerRepository;

    @Transactional(readOnly = true)
    public Owner execute(Long id) {
        return ownerRepository.findById(id)
                .orElseThrow(() -> new OwnerNotFoundException("El dueño no fue encontrado"));
    }
}