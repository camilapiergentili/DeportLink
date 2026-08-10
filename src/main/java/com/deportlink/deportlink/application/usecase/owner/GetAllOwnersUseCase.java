package com.deportlink.deportlink.application.usecase.owner;

import com.deportlink.deportlink.domain.model.Owner;
import com.deportlink.deportlink.domain.port.out.OwnerRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetAllOwnersUseCase {

    private final OwnerRepositoryPort ownerRepository;

    @Transactional(readOnly = true)
    public List<Owner> execute() {
        return ownerRepository.findAll();
    }
}