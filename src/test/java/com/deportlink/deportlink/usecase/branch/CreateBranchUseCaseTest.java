package com.deportlink.deportlink.usecase.branch;

import com.deportlink.deportlink.application.usecase.branch.CreateBranchUseCase;
import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.Club;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClubRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.ClubType;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchAlreadyExistsException;
import com.deportlink.deportlink.exception.ClubNotFoundException;
import com.deportlink.deportlink.exception.ClubNotApprovedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateBranchUseCaseTest {

    @Mock private BranchRepositoryPort branchRepository;
    @Mock private ClubRepositoryPort clubRepository;

    @InjectMocks private CreateBranchUseCase useCase;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static final Long CLUB_ID = 10L;

    private static Address address() {
        return new Address("Av. Corrientes", 1234, "CABA", "Buenos Aires", 1043, -34.603, -58.381);
    }

    private static Club approvedClub() {
        return new Club(CLUB_ID, "Club Test", "Club Test SRL", "30-12345678-9",
                ClubType.SA, VerificationStatus.APPROVED, ActiveStatus.ACTIVE, Set.of(1L));
    }

    private static Club pendingClub() {
        return new Club(CLUB_ID, "Club Test", "Club Test SRL", "30-12345678-9",
                ClubType.SA, VerificationStatus.PENDING, ActiveStatus.INACTIVE, Set.of(1L));
    }

    private static Club rejectedClub() {
        return new Club(CLUB_ID, "Club Test", "Club Test SRL", "30-12345678-9",
                ClubType.SA, VerificationStatus.REJECTED, ActiveStatus.INACTIVE, Set.of(1L));
    }

    // ─── Casos exitosos ──────────────────────────────────────────────────────────

    @Test
    void execute_creaSuccursalEnClubAprobado() {
        Address addr = address();
        Branch saved = new Branch(1L, "Norte", addr, CLUB_ID, VerificationStatus.PENDING, ActiveStatus.INACTIVE);

        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(approvedClub()));
        when(branchRepository.existsByNameIgnoreCaseAndClub("Norte", CLUB_ID)).thenReturn(false);
        when(branchRepository.existsByAddressAndClub(addr, CLUB_ID)).thenReturn(false);
        when(branchRepository.save(any())).thenReturn(saved);

        Branch result = useCase.execute("Norte", addr, CLUB_ID);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Norte");
        assertThat(result.clubId()).isEqualTo(CLUB_ID);
    }

    @Test
    void execute_sucursalGuardadaConEstadoPendingInactive() {
        Address addr = address();
        ArgumentCaptor<Branch> captor = ArgumentCaptor.forClass(Branch.class);
        Branch saved = new Branch(1L, "Norte", addr, CLUB_ID, VerificationStatus.PENDING, ActiveStatus.INACTIVE);

        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(approvedClub()));
        when(branchRepository.existsByNameIgnoreCaseAndClub(any(), any())).thenReturn(false);
        when(branchRepository.existsByAddressAndClub(any(), any())).thenReturn(false);
        when(branchRepository.save(captor.capture())).thenReturn(saved);

        useCase.execute("Norte", addr, CLUB_ID);

        Branch branchPasadaAlRepo = captor.getValue();
        assertThat(branchPasadaAlRepo.id()).isNull();
        assertThat(branchPasadaAlRepo.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(branchPasadaAlRepo.activeStatus()).isEqualTo(ActiveStatus.INACTIVE);
    }

    // ─── Validaciones ────────────────────────────────────────────────────────────

    @Test
    void execute_clubNoExisteLanzaClubNotFound() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("Norte", address(), CLUB_ID))
                .isInstanceOf(ClubNotFoundException.class);

        verify(branchRepository, never()).save(any());
    }

    @Test
    void execute_clubPendienteLanzaClubNotApproved() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(pendingClub()));

        assertThatThrownBy(() -> useCase.execute("Norte", address(), CLUB_ID))
                .isInstanceOf(ClubNotApprovedException.class);

        verify(branchRepository, never()).save(any());
    }

    @Test
    void execute_clubRechazadoLanzaClubNotApproved() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(rejectedClub()));

        assertThatThrownBy(() -> useCase.execute("Norte", address(), CLUB_ID))
                .isInstanceOf(ClubNotApprovedException.class);
    }

    @Test
    void execute_nombreDuplicadoLanzaBranchAlreadyExists() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(approvedClub()));
        when(branchRepository.existsByNameIgnoreCaseAndClub("Norte", CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute("Norte", address(), CLUB_ID))
                .isInstanceOf(BranchAlreadyExistsException.class)
                .hasMessageContaining("Norte");

        verify(branchRepository, never()).save(any());
    }

    @Test
    void execute_direccionDuplicadaLanzaBranchAlreadyExists() {
        Address addr = address();

        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(approvedClub()));
        when(branchRepository.existsByNameIgnoreCaseAndClub("Norte", CLUB_ID)).thenReturn(false);
        when(branchRepository.existsByAddressAndClub(addr, CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute("Norte", addr, CLUB_ID))
                .isInstanceOf(BranchAlreadyExistsException.class)
                .hasMessageContaining("dirección");

        verify(branchRepository, never()).save(any());
    }

    @Test
    void execute_nombreDuplicadoNoLlamaDireccionCheck() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(approvedClub()));
        when(branchRepository.existsByNameIgnoreCaseAndClub("Norte", CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute("Norte", address(), CLUB_ID))
                .isInstanceOf(BranchAlreadyExistsException.class);

        // El short-circuit del nombre evita la segunda query
        verify(branchRepository, never()).existsByAddressAndClub(any(), any());
    }

    // ─── Seguridad / Contrato ────────────────────────────────────────────────────

    @Test
    void execute_siempreVerificaExistenciaDelClubPrimero() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("Norte", address(), CLUB_ID))
                .isInstanceOf(ClubNotFoundException.class);

        // Nunca llega a consultar el repositorio de sucursales
        verifyNoInteractions(branchRepository);
    }
}
