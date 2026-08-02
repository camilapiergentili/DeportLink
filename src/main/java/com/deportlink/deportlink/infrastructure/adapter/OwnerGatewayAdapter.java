package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.OwnerGateway;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.OwnerResponseDto;
import com.deportlink.deportlink.persistence.repository.OwnerRepository;
import com.deportlink.deportlink.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OwnerGatewayAdapter implements OwnerGateway {

    private final OwnerService ownerService;
    private final OwnerRepository ownerRepository;

    /**
     * Delega la creación al OwnerService existente, que ya encapsula:
     * validación de mayoría de edad, unicidad de DNI/CUIL/email, hash de contraseña.
     * Alternativa: reimplementar esa lógica aquí — descartada porque duplicaría el código
     * hasta que Owner migre a Clean Architecture en su propio bounded context.
     */
    @Override
    public Long register(OwnerCommand command) {
        OwnerResponseDto response = ownerService.register(toDto(command));
        return response.getId();
    }

    @Override
    public Optional<OwnerSnapshot> findById(Long id) {
        return ownerRepository.findById(id).map(entity ->
                new OwnerSnapshot(
                        entity.getId(),
                        entity.getFirstName(),
                        entity.getLastName(),
                        entity.getCuil()
                )
        );
    }

    private OwnerRequestDto toDto(OwnerCommand command) {
        OwnerRequestDto dto = new OwnerRequestDto();
        dto.setFirstName(command.firstName());
        dto.setLastName(command.lastName());
        dto.setEmail(command.email());
        dto.setPassword(command.password());
        dto.setPhone(command.phone());
        dto.setDni(command.dni());
        dto.setCuil(command.cuil());
        dto.setDateOfBirth(command.dateOfBirth());
        return dto;
    }
}