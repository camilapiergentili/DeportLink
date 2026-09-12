package com.deportlink.deportlink.usecase.court;

import com.deportlink.deportlink.application.usecase.court.MoveCourtToBranchUseCase;
import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchNotEligibleException;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoveCourtToBranchUseCaseTest {

    @Mock private CourtRepositoryPort courtRepository;
    @Mock private BranchRepositoryPort branchRepository;

    private static final Long COURT_ID = 1L;
    private static final Long OLD_BRANCH_ID = 10L;
    private static final Long NEW_BRANCH_ID = 20L;

    private static Address address() {
        return new Address("Av. Corrientes", 1234, "CABA", "Buenos Aires", 1043, -34.603, -58.381);
    }

    private static Court court() {
        return new Court(COURT_ID, "Cancha 1", 100.0, OLD_BRANCH_ID, 5L, "Football", ActiveStatus.ACTIVE);
    }

    private static Branch branch(VerificationStatus vs, ActiveStatus as) {
        return new Branch(NEW_BRANCH_ID, "Sucursal Sur", address(), 99L, vs, as, 12);
    }

    private MoveCourtToBranchUseCase useCase() {
        return new MoveCourtToBranchUseCase(courtRepository, branchRepository);
    }

    @Test
    void execute_canchaNoEncontrada_lanzaCourtNotFoundException() {
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(COURT_ID, NEW_BRANCH_ID))
                .isInstanceOf(CourtNotFoundException.class);

        verifyNoInteractions(branchRepository);
        verify(courtRepository, never()).save(any());
    }

    @Test
    void execute_sucursalDestinoNoEncontrada_lanzaBranchNotFoundException() {
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(branchRepository.findById(NEW_BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(COURT_ID, NEW_BRANCH_ID))
                .isInstanceOf(BranchNotFoundException.class);

        verify(courtRepository, never()).save(any());
    }

    @Test
    void execute_sucursalDestinoNoAprobada_lanzaBranchNotEligibleException() {
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(branchRepository.findById(NEW_BRANCH_ID))
                .thenReturn(Optional.of(branch(VerificationStatus.PENDING, ActiveStatus.INACTIVE)));

        assertThatThrownBy(() -> useCase().execute(COURT_ID, NEW_BRANCH_ID))
                .isInstanceOf(BranchNotEligibleException.class);

        verify(courtRepository, never()).save(any());
    }

    @Test
    void execute_sucursalDestinoInactiva_lanzaBranchNotEligibleException() {
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(court()));
        // APPROVED pero INACTIVE — igual debe rechazarse, la regla exige las dos condiciones.
        when(branchRepository.findById(NEW_BRANCH_ID))
                .thenReturn(Optional.of(branch(VerificationStatus.APPROVED, ActiveStatus.INACTIVE)));

        assertThatThrownBy(() -> useCase().execute(COURT_ID, NEW_BRANCH_ID))
                .isInstanceOf(BranchNotEligibleException.class);

        verify(courtRepository, never()).save(any());
    }

    @Test
    void execute_movimientoExitoso_actualizaBranchIdYGuarda() {
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(branchRepository.findById(NEW_BRANCH_ID))
                .thenReturn(Optional.of(branch(VerificationStatus.APPROVED, ActiveStatus.ACTIVE)));
        when(courtRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Court result = useCase().execute(COURT_ID, NEW_BRANCH_ID);

        assertThat(result.branchId()).isEqualTo(NEW_BRANCH_ID);
        assertThat(result.id()).isEqualTo(COURT_ID);
        assertThat(result.name()).isEqualTo("Cancha 1"); // el resto de los campos no cambia

        verify(courtRepository).save(argThat(c -> c.branchId().equals(NEW_BRANCH_ID)));
    }

    @Test
    void execute_conReservasFuturas_muevenLaCanchaSinTocarlas() {
        // No hay ninguna acción ni validación sobre reservas en este use case — se confirma
        // que ni siquiera se consulta si la cancha tiene reservas (a diferencia de
        // DeleteCourtUseCase, que sí las bloquea).
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(branchRepository.findById(NEW_BRANCH_ID))
                .thenReturn(Optional.of(branch(VerificationStatus.APPROVED, ActiveStatus.ACTIVE)));
        when(courtRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Court result = useCase().execute(COURT_ID, NEW_BRANCH_ID);

        assertThat(result.branchId()).isEqualTo(NEW_BRANCH_ID);
        verify(courtRepository, never()).hasReservations(any());
    }
}
