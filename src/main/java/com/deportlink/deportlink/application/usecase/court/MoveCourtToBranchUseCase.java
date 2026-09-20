package com.deportlink.deportlink.application.usecase.court;

import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.exception.BranchNotEligibleException;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MoveCourtToBranchUseCase {

    private final CourtRepositoryPort courtRepository;
    private final BranchRepositoryPort branchRepository;

    public Court execute(Long courtId, Long newBranchId) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new CourtNotFoundException("No se encontró la cancha"));

        Branch targetBranch = branchRepository.findById(newBranchId)
                .orElseThrow(() -> new BranchNotFoundException("No se encontró la sucursal destino"));

        // Regla de negocio confirmada: aplica sin excepción, incluso para ADMIN — no es un
        // chequeo de autorización (eso ya lo resuelve el @PreAuthorize del controller), sino
        // una regla de dominio sobre el estado de la sucursal destino.
        if (!targetBranch.isApproved() || !targetBranch.isActive()) {
            throw new BranchNotEligibleException(
                    "La sucursal destino debe estar aprobada y activa para poder mover la cancha");
        }

        // Regla de negocio confirmada: las reservas futuras de la cancha se mantienen tal cual
        // y siguen siendo válidas en la nueva sucursal — no se cancelan ni se valida nada sobre
        // ellas acá.
        return courtRepository.save(court.moveToBranch(newBranchId));
    }
}
