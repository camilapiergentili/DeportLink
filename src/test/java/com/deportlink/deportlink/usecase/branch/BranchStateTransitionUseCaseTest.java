package com.deportlink.deportlink.usecase.branch;

import com.deportlink.deportlink.application.usecase.branch.*;
import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.InvalidStatusTransitionException;
import com.deportlink.deportlink.exception.StatusAlreadyAppliedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchStateTransitionUseCaseTest {

    @Mock private BranchRepositoryPort branchRepository;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static final Long ID = 1L;

    private static Address address() {
        return new Address("Av. Corrientes", 1234, "CABA", "Buenos Aires", 1043, -34.603, -58.381);
    }

    private static Branch pending() {
        return new Branch(ID, "Norte", address(), 10L, VerificationStatus.PENDING, ActiveStatus.INACTIVE);
    }

    private static Branch approved() {
        return new Branch(ID, "Norte", address(), 10L, VerificationStatus.APPROVED, ActiveStatus.ACTIVE);
    }

    private static Branch approvedInactive() {
        return new Branch(ID, "Norte", address(), 10L, VerificationStatus.APPROVED, ActiveStatus.INACTIVE);
    }

    // ─── ApproveBranchUseCase ────────────────────────────────────────────────────

    @Test
    void approve_pendingResultaApprovedActive() {
        Branch savedResult = new Branch(ID, "Norte", address(), 10L, VerificationStatus.APPROVED, ActiveStatus.ACTIVE);
        when(branchRepository.findById(ID)).thenReturn(Optional.of(pending()));
        when(branchRepository.save(any())).thenReturn(savedResult);

        Branch result = new ApproveBranchUseCase(branchRepository).execute(ID);

        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void approve_noEncontradaLanzaBranchNotFound() {
        when(branchRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ApproveBranchUseCase(branchRepository).execute(ID))
                .isInstanceOf(BranchNotFoundException.class);

        verify(branchRepository, never()).save(any());
    }

    @Test
    void approve_aprobadaLanzaInvalidStatusTransition() {
        when(branchRepository.findById(ID)).thenReturn(Optional.of(approved()));

        assertThatThrownBy(() -> new ApproveBranchUseCase(branchRepository).execute(ID))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ─── RejectBranchUseCase ─────────────────────────────────────────────────────

    @Test
    void reject_pendingResultaRejectedInactive() {
        Branch savedResult = new Branch(ID, "Norte", address(), 10L, VerificationStatus.REJECTED, ActiveStatus.INACTIVE);
        when(branchRepository.findById(ID)).thenReturn(Optional.of(pending()));
        when(branchRepository.save(any())).thenReturn(savedResult);

        Branch result = new RejectBranchUseCase(branchRepository).execute(ID);

        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void reject_noEncontradaLanzaBranchNotFound() {
        when(branchRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new RejectBranchUseCase(branchRepository).execute(ID))
                .isInstanceOf(BranchNotFoundException.class);
    }

    @Test
    void reject_aprobadaLanzaInvalidStatusTransition() {
        when(branchRepository.findById(ID)).thenReturn(Optional.of(approved()));

        assertThatThrownBy(() -> new RejectBranchUseCase(branchRepository).execute(ID))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ─── ActivateBranchUseCase ───────────────────────────────────────────────────

    @Test
    void activate_approvedInactiveResultaActive() {
        Branch savedResult = new Branch(ID, "Norte", address(), 10L, VerificationStatus.APPROVED, ActiveStatus.ACTIVE);
        when(branchRepository.findById(ID)).thenReturn(Optional.of(approvedInactive()));
        when(branchRepository.save(any())).thenReturn(savedResult);

        Branch result = new ActivateBranchUseCase(branchRepository).execute(ID);

        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void activate_noEncontradaLanzaBranchNotFound() {
        when(branchRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ActivateBranchUseCase(branchRepository).execute(ID))
                .isInstanceOf(BranchNotFoundException.class);
    }

    @Test
    void activate_pendienteLanzaBranchNotApproved() {
        when(branchRepository.findById(ID)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> new ActivateBranchUseCase(branchRepository).execute(ID))
                .isInstanceOf(BranchNotApprovedException.class);
    }

    @Test
    void activate_yaActivaLanzaStatusAlreadyApplied() {
        when(branchRepository.findById(ID)).thenReturn(Optional.of(approved()));

        assertThatThrownBy(() -> new ActivateBranchUseCase(branchRepository).execute(ID))
                .isInstanceOf(StatusAlreadyAppliedException.class);
    }

    // ─── DeactivateBranchUseCase ─────────────────────────────────────────────────

    @Test
    void deactivate_approvedActiveResultaInactive() {
        Branch savedResult = new Branch(ID, "Norte", address(), 10L, VerificationStatus.APPROVED, ActiveStatus.INACTIVE);
        when(branchRepository.findById(ID)).thenReturn(Optional.of(approved()));
        when(branchRepository.save(any())).thenReturn(savedResult);

        Branch result = new DeactivateBranchUseCase(branchRepository).execute(ID);

        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void deactivate_noEncontradaLanzaBranchNotFound() {
        when(branchRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeactivateBranchUseCase(branchRepository).execute(ID))
                .isInstanceOf(BranchNotFoundException.class);
    }

    @Test
    void deactivate_pendienteLanzaBranchNotApproved() {
        when(branchRepository.findById(ID)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> new DeactivateBranchUseCase(branchRepository).execute(ID))
                .isInstanceOf(BranchNotApprovedException.class);
    }

    @Test
    void deactivate_yaInactivaLanzaStatusAlreadyApplied() {
        when(branchRepository.findById(ID)).thenReturn(Optional.of(approvedInactive()));

        assertThatThrownBy(() -> new DeactivateBranchUseCase(branchRepository).execute(ID))
                .isInstanceOf(StatusAlreadyAppliedException.class);
    }

    // ─── DeleteBranchUseCase ─────────────────────────────────────────────────────

    @Test
    void delete_sucursalExistenteEliminaCorrectamente() {
        when(branchRepository.findById(ID)).thenReturn(Optional.of(approved()));

        new DeleteBranchUseCase(branchRepository).execute(ID);

        verify(branchRepository).delete(ID);
    }

    @Test
    void delete_noEncontradaLanzaBranchNotFound() {
        when(branchRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeleteBranchUseCase(branchRepository).execute(ID))
                .isInstanceOf(BranchNotFoundException.class);

        verify(branchRepository, never()).delete(any());
    }

    @Test
    void delete_verificaExistenciaAntesDeEliminar() {
        when(branchRepository.findById(ID)).thenReturn(Optional.of(approved()));

        new DeleteBranchUseCase(branchRepository).execute(ID);

        // Primero findById, luego delete — nunca delete directo sin verificar
        verify(branchRepository).findById(ID);
        verify(branchRepository).delete(ID);
    }
}