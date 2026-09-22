package com.deportlink.deportlink.application.port.out;

import java.util.Optional;

/**
 * Gateway mínimo hacia el instructor, definido por los casos de uso del módulo de clases —
 * solo verifica existencia. Deliberadamente sin alta ni entidad propia: RegisterInstructorUseCase,
 * InstructorEntity y las credenciales de login quedan diferidos (ver
 * docs/class-management-mvp-design.md). Este gateway es el único punto que una futura
 * infraestructura de Instructor necesita implementar para que CreateClassSlotUseCase funcione.
 */
public interface InstructorGateway {

    Optional<InstructorSnapshot> findById(Long id);

    record InstructorSnapshot(Long id) {}
}
