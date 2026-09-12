package com.deportlink.deportlink.domain;

import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.InvalidCancellationWindowException;
import com.deportlink.deportlink.exception.InvalidStatusTransitionException;
import com.deportlink.deportlink.exception.StatusAlreadyAppliedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BranchDomainTest {

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static Address address() {
        return new Address("Av. Corrientes", 1234, "CABA", "Buenos Aires", 1043, -34.603, -58.381);
    }

    private static Address otherAddress() {
        return new Address("Av. Santa Fe", 900, "CABA", "Buenos Aires", 1059, -34.595, -58.372);
    }

    private static final int WINDOW = 12;

    private static Branch pending() {
        return new Branch(1L, "Norte", address(), 10L, VerificationStatus.PENDING, ActiveStatus.INACTIVE, WINDOW);
    }

    private static Branch approved() {
        return new Branch(1L, "Norte", address(), 10L, VerificationStatus.APPROVED, ActiveStatus.ACTIVE, WINDOW);
    }

    private static Branch approvedInactive() {
        return new Branch(1L, "Norte", address(), 10L, VerificationStatus.APPROVED, ActiveStatus.INACTIVE, WINDOW);
    }

    private static Branch rejected() {
        return new Branch(1L, "Norte", address(), 10L, VerificationStatus.REJECTED, ActiveStatus.INACTIVE, WINDOW);
    }

    // ─── Branch.create() ────────────────────────────────────────────────────────

    @Test
    void create_siempreNacePendingInactive() {
        Branch branch = Branch.create("Norte", address(), 10L, WINDOW);

        assertThat(branch.id()).isNull();
        assertThat(branch.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(branch.activeStatus()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(branch.name()).isEqualTo("Norte");
        assertThat(branch.clubId()).isEqualTo(10L);
        assertThat(branch.cancellationWindowHours()).isEqualTo(WINDOW);
    }

    @Test
    void create_ventanaCeroLanzaInvalidCancellationWindow() {
        assertThatThrownBy(() -> Branch.create("Norte", address(), 10L, 0))
                .isInstanceOf(InvalidCancellationWindowException.class);
    }

    @Test
    void create_ventanaNegativaLanzaInvalidCancellationWindow() {
        assertThatThrownBy(() -> Branch.create("Norte", address(), 10L, -1))
                .isInstanceOf(InvalidCancellationWindowException.class);
    }

    // ─── approve() ──────────────────────────────────────────────────────────────

    @Test
    void approve_pendingPasaAApprovedActive() {
        Branch result = pending().approve();

        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void approve_aprobadaLanzaInvalidStatusTransition() {
        assertThatThrownBy(() -> approved().approve())
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void approve_rechazadaLanzaInvalidStatusTransition() {
        assertThatThrownBy(() -> rejected().approve())
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ─── reject() ───────────────────────────────────────────────────────────────

    @Test
    void reject_pendingPasaARejectedInactive() {
        Branch result = pending().reject();

        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void reject_aprobadaLanzaInvalidStatusTransition() {
        assertThatThrownBy(() -> approved().reject())
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ─── activate() ─────────────────────────────────────────────────────────────

    @Test
    void activate_approvedInactivePasaAActive() {
        Branch result = approvedInactive().activate();

        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);
    }

    @Test
    void activate_yaActivaLanzaStatusAlreadyApplied() {
        assertThatThrownBy(() -> approved().activate())
                .isInstanceOf(StatusAlreadyAppliedException.class);
    }

    @Test
    void activate_noAprobadaLanzaBranchNotApproved() {
        assertThatThrownBy(() -> pending().activate())
                .isInstanceOf(BranchNotApprovedException.class);
    }

    // ─── deactivate() ───────────────────────────────────────────────────────────

    @Test
    void deactivate_approvedActivePasaAInactive() {
        Branch result = approved().deactivate();

        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);
    }

    @Test
    void deactivate_yaInactivaLanzaStatusAlreadyApplied() {
        assertThatThrownBy(() -> approvedInactive().deactivate())
                .isInstanceOf(StatusAlreadyAppliedException.class);
    }

    @Test
    void deactivate_noAprobadaLanzaBranchNotApproved() {
        assertThatThrownBy(() -> pending().deactivate())
                .isInstanceOf(BranchNotApprovedException.class);
    }

    // ─── update() ───────────────────────────────────────────────────────────────

    @Test
    void update_mismaDireccionMantienEstado() {
        Branch original = approved();
        Branch result = original.update("Sucursal Sur", address(), WINDOW);

        assertThat(result.name()).isEqualTo("Sucursal Sur");
        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void update_diferenteDireccionReiniciaPendingInactive() {
        Branch original = approved();
        Branch result = original.update("Sucursal Sur", otherAddress(), WINDOW);

        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(result.address()).isEqualTo(otherAddress());
    }

    @Test
    void update_preservaIdYClubId() {
        Branch original = approved();
        Branch result = original.update("Nuevo nombre", address(), WINDOW);

        assertThat(result.id()).isEqualTo(original.id());
        assertThat(result.clubId()).isEqualTo(original.clubId());
    }

    @Test
    void update_actualizaLaVentanaDeCancelacion() {
        Branch original = approved();
        Branch result = original.update(original.name(), original.address(), 24);

        assertThat(result.cancellationWindowHours()).isEqualTo(24);
    }

    // ─── withId() ───────────────────────────────────────────────────────────────

    @Test
    void withId_preservaTodosLosCamposExceptoId() {
        Branch original = Branch.create("Norte", address(), 10L, WINDOW);
        Branch result = original.withId(99L);

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.name()).isEqualTo(original.name());
        assertThat(result.address()).isEqualTo(original.address());
        assertThat(result.clubId()).isEqualTo(original.clubId());
        assertThat(result.verificationStatus()).isEqualTo(original.verificationStatus());
        assertThat(result.activeStatus()).isEqualTo(original.activeStatus());
        assertThat(result.cancellationWindowHours()).isEqualTo(original.cancellationWindowHours());
    }

    // ─── Queries de estado ───────────────────────────────────────────────────────

    @Test
    void isPending_soloParaEstadoPending() {
        assertThat(pending().isPending()).isTrue();
        assertThat(approved().isPending()).isFalse();
        assertThat(rejected().isPending()).isFalse();
    }

    @Test
    void isApproved_soloParaEstadoApproved() {
        assertThat(approved().isApproved()).isTrue();
        assertThat(pending().isApproved()).isFalse();
        assertThat(rejected().isApproved()).isFalse();
    }

    @Test
    void isActive_soloParaEstadoActive() {
        assertThat(approved().isActive()).isTrue();
        assertThat(approvedInactive().isActive()).isFalse();
    }
}