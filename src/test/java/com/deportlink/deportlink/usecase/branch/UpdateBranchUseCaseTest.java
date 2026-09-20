package com.deportlink.deportlink.usecase.branch;

import com.deportlink.deportlink.application.usecase.branch.UpdateBranchUseCase;
import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchAlreadyExistsException;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateBranchUseCaseTest {

    @Mock private BranchRepositoryPort branchRepository;

    @InjectMocks private UpdateBranchUseCase useCase;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static final Long BRANCH_ID = 1L;
    private static final Long CLUB_ID = 10L;
    private static final int WINDOW = 12;

    private static Address address() {
        return new Address("Av. Corrientes", 1234, "CABA", "Buenos Aires", 1043, -34.603, -58.381);
    }

    private static Address otherAddress() {
        return new Address("Av. Santa Fe", 900, "CABA", "Buenos Aires", 1059, -34.595, -58.372);
    }

    private static Branch approvedBranch() {
        return new Branch(BRANCH_ID, "Norte", address(), CLUB_ID, VerificationStatus.APPROVED, ActiveStatus.ACTIVE, WINDOW);
    }

    // ─── Casos exitosos ──────────────────────────────────────────────────────────

    @Test
    void execute_cambiaSoloElNombreMantieneEstado() {
        Branch existing = approvedBranch();
        Branch saved = new Branch(BRANCH_ID, "Sur", address(), CLUB_ID, VerificationStatus.APPROVED, ActiveStatus.ACTIVE, WINDOW);

        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(existing));
        when(branchRepository.existsByNameIgnoreCaseAndClub("Sur", CLUB_ID)).thenReturn(false);
        when(branchRepository.save(any())).thenReturn(saved);

        Branch result = useCase.execute(BRANCH_ID, "Sur", address(), WINDOW);

        assertThat(result.name()).isEqualTo("Sur");
        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void execute_cambiaDireccionReiniciaAPendingInactive() {
        Branch existing = approvedBranch();
        Branch saved = new Branch(BRANCH_ID, "Norte", otherAddress(), CLUB_ID, VerificationStatus.PENDING, ActiveStatus.INACTIVE, WINDOW);

        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(existing));
        when(branchRepository.existsByAddressAndClub(otherAddress(), CLUB_ID)).thenReturn(false);
        when(branchRepository.save(any())).thenReturn(saved);

        Branch result = useCase.execute(BRANCH_ID, "Norte", otherAddress(), WINDOW);

        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void execute_mismoNombreMismaDireccionNoValidaDuplicados() {
        Branch existing = approvedBranch();
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(existing));
        when(branchRepository.save(any())).thenReturn(existing);

        useCase.execute(BRANCH_ID, "Norte", address(), WINDOW);

        // Si no cambió ni nombre ni dirección, no se llaman las validaciones de unicidad
        verify(branchRepository, never()).existsByNameIgnoreCaseAndClub(any(), any());
        verify(branchRepository, never()).existsByAddressAndClub(any(), any());
    }

    @Test
    void execute_cambiaDireccionNoValidaNombre() {
        Branch existing = approvedBranch();
        Address newAddr = otherAddress();
        Branch saved = new Branch(BRANCH_ID, "Norte", newAddr, CLUB_ID, VerificationStatus.PENDING, ActiveStatus.INACTIVE, WINDOW);

        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(existing));
        when(branchRepository.existsByAddressAndClub(newAddr, CLUB_ID)).thenReturn(false);
        when(branchRepository.save(any())).thenReturn(saved);

        useCase.execute(BRANCH_ID, "Norte", newAddr, WINDOW);

        // El nombre no cambió, no se debe verificar unicidad de nombre
        verify(branchRepository, never()).existsByNameIgnoreCaseAndClub(any(), any());
    }

    @Test
    void execute_cambiaNombreNoValidaDireccion() {
        Branch existing = approvedBranch();
        Branch saved = new Branch(BRANCH_ID, "Sur", address(), CLUB_ID, VerificationStatus.APPROVED, ActiveStatus.ACTIVE, WINDOW);

        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(existing));
        when(branchRepository.existsByNameIgnoreCaseAndClub("Sur", CLUB_ID)).thenReturn(false);
        when(branchRepository.save(any())).thenReturn(saved);

        useCase.execute(BRANCH_ID, "Sur", address(), WINDOW);

        // La dirección no cambió, no se debe verificar unicidad de dirección
        verify(branchRepository, never()).existsByAddressAndClub(any(), any());
    }

    // ─── Validaciones ────────────────────────────────────────────────────────────

    @Test
    void execute_sucursalNoEncontradaLanzaBranchNotFound() {
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(BRANCH_ID, "Sur", address(), WINDOW))
                .isInstanceOf(BranchNotFoundException.class);

        verify(branchRepository, never()).save(any());
    }

    @Test
    void execute_nombreDuplicadoLanzaBranchAlreadyExists() {
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        when(branchRepository.existsByNameIgnoreCaseAndClub("Sur", CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(BRANCH_ID, "Sur", address(), WINDOW))
                .isInstanceOf(BranchAlreadyExistsException.class);

        verify(branchRepository, never()).save(any());
    }

    @Test
    void execute_direccionDuplicadaLanzaBranchAlreadyExists() {
        Address newAddr = otherAddress();
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        when(branchRepository.existsByAddressAndClub(newAddr, CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(BRANCH_ID, "Norte", newAddr, WINDOW))
                .isInstanceOf(BranchAlreadyExistsException.class);

        verify(branchRepository, never()).save(any());
    }
}