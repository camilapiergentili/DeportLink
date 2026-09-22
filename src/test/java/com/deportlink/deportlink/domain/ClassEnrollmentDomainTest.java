package com.deportlink.deportlink.domain;

import com.deportlink.deportlink.domain.model.ClassEnrollment;
import com.deportlink.deportlink.exception.StatusAlreadyAppliedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ClassEnrollmentDomainTest {

    // ─── create() — caso válido ─────────────────────────────────────────────────

    @Test
    void create_datosValidos_naceActivo() {
        ClassEnrollment result = ClassEnrollment.create(1L, 2L);

        assertThat(result.id()).isNull();
        assertThat(result.classSlotId()).isEqualTo(1L);
        assertThat(result.playerId()).isEqualTo(2L);
        assertThat(result.active()).isTrue();
    }

    // ─── create() — datos obligatorios ──────────────────────────────────────────

    @Test
    void create_classSlotNulo_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassEnrollment.create(null, 2L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_playerNulo_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassEnrollment.create(1L, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ─── deactivate() / activate() ──────────────────────────────────────────────

    @Test
    void deactivate_enrollmentActivo_pasaAInactivoSinPerderDatos() {
        ClassEnrollment result = ClassEnrollment.create(1L, 2L).deactivate();

        assertThat(result.active()).isFalse();
        assertThat(result.classSlotId()).isEqualTo(1L);
        assertThat(result.playerId()).isEqualTo(2L);
    }

    @Test
    void deactivate_yaInactivo_lanzaStatusAlreadyApplied() {
        ClassEnrollment inactive = ClassEnrollment.create(1L, 2L).deactivate();

        assertThatThrownBy(inactive::deactivate).isInstanceOf(StatusAlreadyAppliedException.class);
    }

    @Test
    void activate_enrollmentInactivo_reincorporaAlGrupo() {
        ClassEnrollment inactive = ClassEnrollment.create(1L, 2L).deactivate();

        ClassEnrollment result = inactive.activate();

        assertThat(result.active()).isTrue();
    }

    @Test
    void activate_yaActivo_lanzaStatusAlreadyApplied() {
        ClassEnrollment active = ClassEnrollment.create(1L, 2L);

        assertThatThrownBy(active::activate).isInstanceOf(StatusAlreadyAppliedException.class);
    }

    // ─── withId() ───────────────────────────────────────────────────────────────

    @Test
    void withId_preservaTodosLosCamposExceptoId() {
        ClassEnrollment original = ClassEnrollment.create(1L, 2L);
        ClassEnrollment result = original.withId(50L);

        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.classSlotId()).isEqualTo(original.classSlotId());
        assertThat(result.playerId()).isEqualTo(original.playerId());
        assertThat(result.active()).isEqualTo(original.active());
    }
}
