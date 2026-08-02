package com.deportlink.deportlink.application.usecase.club;

import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.ClubNotActivedException;
import com.deportlink.deportlink.exception.ClubNotApprovedException;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetClubUseCase {

    private final ClubRepositoryPort clubRepository;

    /**
     * Endpoint público: solo devuelve clubs aprobados y activos.
     * Lanzar NotFound en lugar de Forbidden para PENDING/REJECTED evita exponer
     * si un club existe pero no está aprobado (seguridad por oscuridad).
     */
    @Transactional(readOnly = true)
    public Club execute(Long id) {
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new ClubNotFoundException("El club no se encontró"));

        if (club.verificationStatus() == VerificationStatus.PENDING) {
            throw new ClubNotApprovedException("El club no se puede mostrar, la documentación está siendo revisada");
        }
        if (club.verificationStatus() == VerificationStatus.REJECTED) {
            throw new ClubNotApprovedException("El club no ha pasado la verificación de documentación");
        }
        if (club.activeStatus() == ActiveStatus.INACTIVE) {
            throw new ClubNotActivedException("El club no se encuentra activo");
        }

        return club;
    }
}