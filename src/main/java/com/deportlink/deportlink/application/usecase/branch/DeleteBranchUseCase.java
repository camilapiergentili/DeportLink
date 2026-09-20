package com.deportlink.deportlink.application.usecase.branch;

import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.exception.BranchHasReservationsException;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class DeleteBranchUseCase {

    private final BranchRepositoryPort branchRepository;

    public void execute(Long id) {
        // Lock pesimista sobre las canchas de la sucursal ANTES de confirmar que la sucursal
        // existe — a propósito, aunque parezca al revés. Tiene que ser la primera lectura de
        // la transacción (ver BranchRepositoryPort.lockCourtsForUpdate); si findById corriera
        // primero, ese SELECT plano fijaría el snapshot de REPEATABLE READ de MySQL antes del
        // lock, y hasReservations() más abajo podría seguir viendo datos anteriores al commit
        // de una reserva confirmándose en paralelo sobre alguna cancha de esta sucursal (mismo
        // bug que se corrigió en RescheduleReservationUseCase). El lock por branchId no
        // distingue "no existe" de "existe sin canchas" — por eso el findById de abajo sigue
        // haciendo falta para el BranchNotFoundException, solo que ahora corre después.
        branchRepository.lockCourtsForUpdate(id);

        branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("Sucursal no encontrada"));

        // Reservation es historial — nunca se borra en cascada. Si la sucursal, o
        // alguna de sus canchas, tiene una reserva asociada, no se puede eliminar.
        // Si no tiene ninguna, se permite eliminar la estructura (courts vacías
        // incluidas) tal como ya lo hace el cascade Branch→Court existente.
        if (branchRepository.hasReservations(id)) {
            throw new BranchHasReservationsException(
                    "No se puede eliminar la sucursal: ella o alguna de sus canchas tiene reservas asociadas");
        }

        branchRepository.delete(id);
    }
}