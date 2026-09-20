package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.InstructorGateway;
import com.deportlink.deportlink.persistence.repository.InstructorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InstructorGatewayAdapter implements InstructorGateway {

    private final InstructorRepository instructorRepository;

    @Override
    public Optional<InstructorSnapshot> findById(Long id) {
        return instructorRepository.findById(id).map(entity -> new InstructorSnapshot(entity.getId()));
    }
}
