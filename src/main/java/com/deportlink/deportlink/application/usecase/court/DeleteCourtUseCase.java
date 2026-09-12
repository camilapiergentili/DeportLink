package com.deportlink.deportlink.application.usecase.court;

import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.exception.CourtHasReservationsException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class DeleteCourtUseCase {

    private final CourtRepositoryPort courtRepository;

    public void execute(Long id) {
        // Lock pesimista — primera lectura de la transacción, antes de chequear reservas.
        // Sin esto, "no tiene reservas" podría verificarse contra un snapshot desactualizado
        // si hay una reserva confirmándose en paralelo (mismo mecanismo que
        // BookReservationUseCase / el fix de RescheduleReservationUseCase).
        courtRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));

        // Reservation es historial — nunca se borra en cascada. Si la cancha tiene
        // alguna reserva (de cualquier estado), no se puede eliminar.
        if (courtRepository.hasReservations(id)) {
            throw new CourtHasReservationsException(
                    "No se puede eliminar la cancha: tiene reservas asociadas");
        }

        courtRepository.delete(id);
    }
}