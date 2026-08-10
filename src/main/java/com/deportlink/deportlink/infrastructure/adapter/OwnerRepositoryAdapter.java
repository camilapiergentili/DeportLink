package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.model.Owner;
import com.deportlink.deportlink.domain.port.out.OwnerRepositoryPort;
import com.deportlink.deportlink.exception.OwnerNotFoundException;
import com.deportlink.deportlink.model.Rol;
import com.deportlink.deportlink.model.entity.OwnerEntity;
import com.deportlink.deportlink.persistence.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OwnerRepositoryAdapter implements OwnerRepositoryPort {

    private final OwnerRepository ownerRepository;

    @Override
    public Owner save(Owner owner) {
        OwnerEntity entity;
        if (owner.id() != null) {
            entity = ownerRepository.findById(owner.id())
                    .orElseThrow(() -> new OwnerNotFoundException("El dueño no fue encontrado"));
            entity.setFirstName(owner.firstName());
            entity.setLastName(owner.lastName());
            entity.setEmail(owner.email());
            entity.setPhone(owner.phone());
            entity.setDni(owner.dni());
            entity.setCuil(owner.cuil());
            if (owner.password() != null) {
                entity.setPassword(owner.password());
            }
        } else {
            entity = new OwnerEntity();
            entity.setFirstName(owner.firstName());
            entity.setLastName(owner.lastName());
            entity.setEmail(owner.email());
            entity.setPhone(owner.phone());
            entity.setPassword(owner.password());
            entity.setDni(owner.dni());
            entity.setCuil(owner.cuil());
            entity.setDateOfBirth(owner.dateOfBirth());
            entity.setRole(Rol.OWNER);
        }
        return toOwner(ownerRepository.save(entity));
    }

    @Override
    public Optional<Owner> findById(Long id) {
        return ownerRepository.findById(id).map(this::toOwner);
    }

    @Override
    public List<Owner> findAll() {
        return ownerRepository.findAll().stream().map(this::toOwner).toList();
    }

    @Override
    public void delete(Long id) {
        ownerRepository.deleteById(id);
    }

    @Override
    public boolean existsByDni(long dni) {
        return ownerRepository.findByDni(dni).isPresent();
    }

    @Override
    public boolean existsByCuil(String cuil) {
        return ownerRepository.findByCuil(cuil).isPresent();
    }

    @Override
    public boolean existsByEmail(String email) {
        return ownerRepository.findByEmail(email).isPresent();
    }

    private Owner toOwner(OwnerEntity e) {
        return new Owner(e.getId(), e.getFirstName(), e.getLastName(), e.getEmail(),
                e.getPhone(), e.getDni(), e.getCuil(), e.getDateOfBirth(), null);
    }
}