package com.deportlink.deportlink.usecase.club;

import com.deportlink.deportlink.application.usecase.club.DeleteClubUseCase;
import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.ClubType;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.ClubHasBranchesException;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteClubUseCaseTest {

    @Mock private ClubRepositoryPort clubRepository;

    private static final Long ID = 1L;

    private static Club club() {
        return new Club(ID, "Club Norte", "Club Norte SA", "30111111111",
                ClubType.SA, VerificationStatus.APPROVED, ActiveStatus.ACTIVE, Set.of(10L));
    }

    @Test
    void delete_clubSinSucursales_eliminaCorrectamente() {
        when(clubRepository.findById(ID)).thenReturn(Optional.of(club()));
        when(clubRepository.hasBranches(ID)).thenReturn(false);

        new DeleteClubUseCase(clubRepository).execute(ID);

        verify(clubRepository).delete(ID);
    }

    @Test
    void delete_clubConSucursales_lanzaClubHasBranchesException() {
        when(clubRepository.findById(ID)).thenReturn(Optional.of(club()));
        when(clubRepository.hasBranches(ID)).thenReturn(true);

        assertThatThrownBy(() -> new DeleteClubUseCase(clubRepository).execute(ID))
                .isInstanceOf(ClubHasBranchesException.class);

        verify(clubRepository, never()).delete(any());
    }

    @Test
    void delete_noEncontradoLanzaClubNotFound() {
        when(clubRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeleteClubUseCase(clubRepository).execute(ID))
                .isInstanceOf(ClubNotFoundException.class);

        verify(clubRepository, never()).delete(any());
        verify(clubRepository, never()).hasBranches(any());
    }
}
