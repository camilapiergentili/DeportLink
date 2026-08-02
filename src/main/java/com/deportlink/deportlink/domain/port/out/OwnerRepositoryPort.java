package com.deportlink.deportlink.domain.port.out;

import com.deportlink.deportlink.domain.model.Owner;

import java.util.List;
import java.util.Optional;

public interface OwnerRepositoryPort {
    Owner save(Owner owner);
    Optional<Owner> findById(Long id);
    List<Owner> findAll();
    void delete(Long id);
    boolean existsByDni(long dni);
    boolean existsByCuil(String cuil);
    boolean existsByEmail(String email);
}