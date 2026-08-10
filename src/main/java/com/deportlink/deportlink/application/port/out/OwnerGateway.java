package com.deportlink.deportlink.application.port.out;

import java.util.Optional;

/**
 * Gateway hacia el bounded context Owner, definido por los casos de uso de Club.
 * Solo expone lo que Club necesita: verificar existencia y registrar nuevos dueños.
 */
public interface OwnerGateway {

    /**
     * Registra un nuevo dueño y devuelve su ID generado.
     * El gateway encapsula toda la lógica de creación (validaciones, hash de contraseña)
     * que vive en OwnerService — el caso de uso no necesita conocerla.
     */
    Long register(OwnerCommand command);

    Optional<OwnerSnapshot> findById(Long id);

    record OwnerCommand(
            String firstName,
            String lastName,
            String email,
            String password,
            String phone,
            long dni,
            String cuil,
            String dateOfBirth
    ) {}

    record OwnerSnapshot(Long id, String firstName, String lastName, String cuil) {}
}