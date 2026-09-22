package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.ClassSlotCourtGateway;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.persistence.repository.CourtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClassSlotCourtGatewayAdapter implements ClassSlotCourtGateway {

    private final CourtRepository courtRepository;

    @Override
    public Optional<Long> findCourtIdByClassSlotForUpdate(Long classSlotId) {
        return courtRepository.findByClassSlotIdForUpdate(classSlotId).map(CourtEntity::getId);
    }
}
