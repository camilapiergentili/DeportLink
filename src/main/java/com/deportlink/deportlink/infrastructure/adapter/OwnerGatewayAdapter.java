package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.OwnerGateway;
import com.deportlink.deportlink.domain.model.Owner;
import com.deportlink.deportlink.domain.port.out.OwnerRepositoryPort;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;
import com.deportlink.deportlink.exception.UnderageException;
import com.deportlink.deportlink.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OwnerGatewayAdapter implements OwnerGateway {

    private final OwnerRepositoryPort ownerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Long register(OwnerCommand command) {
        if (ownerRepository.existsByDni(command.dni())) {
            throw new OwnerAlreadyExistsException("El dueño con dni " + command.dni() + " ya se encuentra registrado");
        }
        if (ownerRepository.existsByCuil(command.cuil())) {
            throw new OwnerAlreadyExistsException("El dueño con número de cuil: " + command.cuil() + " ya se encuentra registrado");
        }
        if (ownerRepository.existsByEmail(command.email())) {
            throw new OwnerAlreadyExistsException("El dueño con email: " + command.email() + " ya se encuentra registrado");
        }

        LocalDate dateOfBirth = LocalDate.parse(command.dateOfBirth(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if (!DateUtils.isOfLegalAge(dateOfBirth)) {
            throw new UnderageException("Para registrar un club debes ser mayor de edad");
        }

        String encodedPassword = passwordEncoder.encode(command.password());
        Owner owner = new Owner(null, command.firstName(), command.lastName(), command.email(),
                command.phone(), command.dni(), command.cuil(), dateOfBirth, encodedPassword);

        return ownerRepository.save(owner).id();
    }

    @Override
    public Optional<OwnerSnapshot> findById(Long id) {
        return ownerRepository.findById(id).map(owner ->
                new OwnerSnapshot(owner.id(), owner.firstName(), owner.lastName(), owner.cuil())
        );
    }
}