package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.persistence.repository.CourtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CourtGatewayAdapter implements CourtGateway {

    private final CourtRepository courtRepository;

    @Override
    public Optional<CourtSnapshot> findById(Long id) {
        return courtRepository.findByIdWithRelations(id).map(this::toSnapshot);
    }

    @Override
    public Optional<CourtSnapshot> findByIdForUpdate(Long id) {
        // JOIN FETCH + PESSIMISTIC_WRITE: una sola query bloquea la fila y carga las relaciones.
        // Alternativa descartada: dos queries (lock + eager load por separado) — el lock quedaría
        // sobre una query distinta de la que carga datos, confundiendo al siguiente desarrollador.
        return courtRepository.findByIdForUpdateWithRelations(id).map(this::toSnapshot);
    }

    private CourtSnapshot toSnapshot(CourtEntity entity) {
        return new CourtSnapshot(
                entity.getId(),
                entity.getPricePerHour(),
                entity.getName(),
                entity.getSport().getNameSport(),
                entity.getBranch().getName(),
                formatAddress(entity.getBranch().getAddress())
        );
    }

    private String formatAddress(AddressEntity address) {
        return address.getStreetName() + " " + address.getNumber() + ", " + address.getCity();
    }
}