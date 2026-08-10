package com.deportlink.deportlink.application.usecase.owner;

import com.deportlink.deportlink.domain.model.Owner;
import com.deportlink.deportlink.domain.port.out.OwnerRepositoryPort;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.exception.OwnerAlreadyExistsException;
import com.deportlink.deportlink.exception.UnderageException;
import com.deportlink.deportlink.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterOwnerUseCase {

    private final OwnerRepositoryPort ownerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Owner execute(OwnerRequestDto dto) {
        log.info("Registering owner: email={}, dni={}", dto.getEmail(), dto.getDni());

        if (ownerRepository.existsByDni(dto.getDni())) {
            throw new OwnerAlreadyExistsException("El dueño con dni " + dto.getDni() + " ya se encuentra registrado");
        }
        if (ownerRepository.existsByCuil(dto.getCuil())) {
            throw new OwnerAlreadyExistsException("El dueño con número de cuil: " + dto.getCuil() + " ya se encuentra registrado");
        }
        if (ownerRepository.existsByEmail(dto.getEmail())) {
            throw new OwnerAlreadyExistsException("El dueño con email: " + dto.getEmail() + " ya se encuentra registrado");
        }

        LocalDate dateOfBirth = LocalDate.parse(dto.getDateOfBirth(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if (!DateUtils.isOfLegalAge(dateOfBirth)) {
            throw new UnderageException("Para registrar un club debes ser mayor de edad");
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());
        Owner owner = new Owner(null, dto.getFirstName(), dto.getLastName(), dto.getEmail(),
                dto.getPhone(), dto.getDni(), dto.getCuil(), dateOfBirth, encodedPassword);

        Owner saved = ownerRepository.save(owner);
        log.info("Owner registered: ownerId={}", saved.id());
        return saved;
    }
}