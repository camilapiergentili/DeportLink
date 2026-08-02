package com.deportlink.deportlink.domain;

import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
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

    private static Branch pending() {
        return new Branch(1L, "Norte", address(), 10L, VerificationStatus.PENDING, ActiveStatus.INACTIVE);
    }

    private static Branch approved() {
        return new Branch(1L, "Norte", address(), 10L, VerificationStatus.APPROVED, ActiveStatus.ACTIVE);
    }

    private static Branch approvedInactive() {
        return new Branch(1L, "Norte", address(), 10L, VerificationStatus.APPROVED, ActiveStatus.INACTIVE);
    }

    private static Branch rejected() {
        return new Branch(1L, "Norte", address(), 10L, VerificationStatus.REJECTED, ActiveStatus.INACTIVE);
    }

    // ─── Branch.create() ────────────────────────────────────────────────────────

    @Test
    void create_siempreNacePendingInactive() {
        Branch branch = Branch.create("Norte", address(), 10L);

        assertThat(branch.id()).isNull();
        assertThat(branch.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(branch.activeStatus()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(branch.name()).isEqualTo("Norte");
        assertThat(branch.clubId()).isEqualTo(10L);
    }

    // ─── approve() ──────────────────────────────────────────────────────────────

    @Test
    void approve_pendingPasaAApprovedActive() {
        Branch result = pending().approve();

        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void approve_aprobadaLanzaIllegalState() {
        assertThatThrownBy(() -> approved().approve())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approve_rechazadaLanzaIllegalState() {
        assertThatThrownBy(() -> rejected().approve())
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── reject() ───────────────────────────────────────────────────────────────

    @Test
    void reject_pendingPasaARejectedInactive() {
        Branch result = pending().reject();

        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.INACTIVE);
    }

    @Test
    void reject_aprobadaLanzaIllegalState() {
        assertThatThrownBy(() -> approved().reject())
                .isInstanceOf(IllegalStateException.class);
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
        Branch result = original.update("Sucursal Sur", address());

        assertThat(result.name()).isEqualTo("Sucursal Sur");
        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void update_diferenteDireccionReiniciaPendingInactive() {
        Branch original = approved();
        Branch result = original.update("Sucursal Sur", otherAddress());

        assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(result.activeStatus()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(result.address()).isEqualTo(otherAddress());
    }

    @Test
    void update_preservaIdYClubId() {
        Branch original = approved();
        Branch result = original.update("Nuevo nombre", address());

        assertThat(result.id()).isEqualTo(original.id());
        assertThat(result.clubId()).isEqualTo(original.clubId());
    }

    // ─── withId() ───────────────────────────────────────────────────────────────

    @Test
    void withId_preservaTodosLosCamposExceptoId() {
        Branch original = Branch.create("Norte", address(), 10L);
        Branch result = original.withId(99L);

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.name()).isEqualTo(original.name());
        assertThat(result.address()).isEqualTo(original.address());
        assertThat(result.clubId()).isEqualTo(original.clubId());
        assertThat(result.verificationStatus()).isEqualTo(original.verificationStatus());
        assertThat(result.activeStatus()).isEqualTo(original.activeStatus());
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