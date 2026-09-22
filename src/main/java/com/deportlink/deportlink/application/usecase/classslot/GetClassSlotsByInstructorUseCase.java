package com.deportlink.deportlink.application.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.exception.InstructorNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lista los ClassSlot de un instructor, activos y pausados por igual — el filtrado por estado
 * es una decisión de presentación, no de esta consulta. Devuelve lista vacía si no tiene
 * horarios; no distingue ese caso de "el instructorId no existe" (no se consulta InstructorGateway
 * acá — ver spec de esta etapa, sección 6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetClassSlotsByInstructorUseCase {

    private final ClassSlotRepositoryPort classSlotRepository;

    @Transactional(readOnly = true)
    public List<ClassSlot> execute(Actor actor, Long instructorId) {
        if (!actor.canAccess(instructorId)) {
            throw new InstructorNotFoundException("No se encontró el instructor");
        }
        return classSlotRepository.findAllByInstructorId(instructorId);
    }
}
