package com.deportlink.deportlink.usecase.branch;

import com.deportlink.deportlink.application.usecase.branch.GetBranchesBySportUseCase;
import com.deportlink.deportlink.application.usecase.branch.GetNearbyBranchesUseCase;
import com.deportlink.deportlink.application.usecase.branch.SearchBranchesByNameUseCase;
import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchSearchUseCaseTest {

    @Mock private BranchRepositoryPort branchRepository;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static Branch branch(Long id, String name, double lat, double lng) {
        Address addr = new Address("Calle", 1, "CABA", "Buenos Aires", 1000, lat, lng);
        return new Branch(id, name, addr, 10L, VerificationStatus.APPROVED, ActiveStatus.ACTIVE, 12);
    }

    // ─── SearchBranchesByNameUseCase ─────────────────────────────────────────────

    @Test
    void searchByName_retornaResultadosDelRepositorio() {
        List<Branch> expected = List.of(branch(1L, "Norte", -34.6, -58.4));
        when(branchRepository.searchApprovedByName("norte")).thenReturn(expected);

        List<Branch> result = new SearchBranchesByNameUseCase(branchRepository).execute("norte");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Norte");
    }

    @Test
    void searchByName_retornaListaVaciaSiNoHayCoincidencias() {
        when(branchRepository.searchApprovedByName("xyz")).thenReturn(List.of());

        List<Branch> result = new SearchBranchesByNameUseCase(branchRepository).execute("xyz");

        assertThat(result).isEmpty();
    }

    @Test
    void searchByName_aplicaTrimAlTerminoAntesDeDelegarDelegando() {
        when(branchRepository.searchApprovedByName("norte")).thenReturn(List.of());

        new SearchBranchesByNameUseCase(branchRepository).execute("  norte  ");

        // trim() debe haberse aplicado — el repo recibe "norte" sin espacios
        verify(branchRepository).searchApprovedByName("norte");
    }

    @Test
    void searchByName_termInoBlancoTrimResultaEnStringVacio() {
        when(branchRepository.searchApprovedByName("")).thenReturn(List.of());

        new SearchBranchesByNameUseCase(branchRepository).execute("   ");

        verify(branchRepository).searchApprovedByName("");
    }

    // ─── GetBranchesBySportUseCase ───────────────────────────────────────────────

    @Test
    void getBySport_retornaSucursalesConEseDeporte() {
        Long sportId = 3L;
        List<Branch> expected = List.of(branch(1L, "Norte", -34.6, -58.4), branch(2L, "Sur", -34.7, -58.5));
        when(branchRepository.findApprovedBySport(sportId)).thenReturn(expected);

        List<Branch> result = new GetBranchesBySportUseCase(branchRepository).execute(sportId);

        assertThat(result).hasSize(2);
    }

    @Test
    void getBySport_retornaListaVaciaSiNingunaSuccursalTieneEseDeporte() {
        when(branchRepository.findApprovedBySport(99L)).thenReturn(List.of());

        List<Branch> result = new GetBranchesBySportUseCase(branchRepository).execute(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void getBySport_delegaAlRepositorioConElSportIdCorrecto() {
        Long sportId = 5L;
        when(branchRepository.findApprovedBySport(sportId)).thenReturn(List.of());

        new GetBranchesBySportUseCase(branchRepository).execute(sportId);

        verify(branchRepository).findApprovedBySport(sportId);
        verifyNoMoreInteractions(branchRepository);
    }

    // ─── GetNearbyBranchesUseCase ────────────────────────────────────────────────

    @Test
    void getNearby_retornaSucursalesDentroDelRadio() {
        double lat = -34.603, lng = -58.381, radiusKm = 5.0;
        List<Branch> expected = List.of(branch(1L, "Norte", -34.61, -58.39));
        when(branchRepository.findNearby(lat, lng, radiusKm)).thenReturn(expected);

        List<Branch> result = new GetNearbyBranchesUseCase(branchRepository).execute(lat, lng, radiusKm);

        assertThat(result).hasSize(1);
    }

    @Test
    void getNearby_retornaListaVaciaSiNoHaySucursalesCercanas() {
        when(branchRepository.findNearby(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());

        List<Branch> result = new GetNearbyBranchesUseCase(branchRepository).execute(-34.603, -58.381, 1.0);

        assertThat(result).isEmpty();
    }

    @Test
    void getNearby_delegaLatLngYRadiusAlRepositorioExactamente() {
        double lat = -34.603, lng = -58.381, radiusKm = 10.0;
        when(branchRepository.findNearby(lat, lng, radiusKm)).thenReturn(List.of());

        new GetNearbyBranchesUseCase(branchRepository).execute(lat, lng, radiusKm);

        verify(branchRepository).findNearby(lat, lng, radiusKm);
        verifyNoMoreInteractions(branchRepository);
    }

    @Test
    void getNearby_radioMinimoCero_delegaIgual() {
        when(branchRepository.findNearby(-34.603, -58.381, 0.0)).thenReturn(List.of());

        List<Branch> result = new GetNearbyBranchesUseCase(branchRepository).execute(-34.603, -58.381, 0.0);

        assertThat(result).isEmpty();
        verify(branchRepository).findNearby(-34.603, -58.381, 0.0);
    }
}