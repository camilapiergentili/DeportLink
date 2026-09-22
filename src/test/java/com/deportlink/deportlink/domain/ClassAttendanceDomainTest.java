package com.deportlink.deportlink.domain;

import com.deportlink.deportlink.domain.model.ClassAttendance;
import com.deportlink.deportlink.enums.ClassAttendanceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ClassAttendanceDomainTest {

    // ─── createPending() — caso válido ──────────────────────────────────────────

    @Test
    void createPending_datosValidos_naceEnPending() {
        ClassAttendance result = ClassAttendance.createPending(1L, 2L);

        assertThat(result.id()).isNull();
        assertThat(result.classSessionId()).isEqualTo(1L);
        assertThat(result.playerId()).isEqualTo(2L);
        assertThat(result.status()).isEqualTo(ClassAttendanceStatus.PENDING);
    }

    // ─── createPending() — datos obligatorios ───────────────────────────────────

    @Test
    void createPending_classSessionNula_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassAttendance.createPending(null, 2L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createPending_playerNulo_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassAttendance.createPending(1L, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ─── confirm() ──────────────────────────────────────────────────────────────

    @Test
    void confirm_pendiente_pasaAConfirmed() {
        ClassAttendance result = ClassAttendance.createPending(1L, 2L).confirm();

        assertThat(result.status()).isEqualTo(ClassAttendanceStatus.CONFIRMED);
    }

    // Decisión de diseño explícita (docs/class-management-mvp-design.md, sección 7 regla 8):
    // confirmar/cancelar es idempotente, no una máquina de estados estricta como Reservation —
    // no hay ventana de cancelación ni cargo económico, es un registro manual de WhatsApp que
    // puede cambiar de opinión. Este test deja esa decisión explícita, no implícita.
    @Test
    void confirm_yaConfirmada_siguePermitiendoloYQuedaConfirmed() {
        ClassAttendance confirmed = ClassAttendance.createPending(1L, 2L).confirm();

        ClassAttendance result = confirmed.confirm();

        assertThat(result.status()).isEqualTo(ClassAttendanceStatus.CONFIRMED);
    }

    @Test
    void confirm_cancelada_puedeVolverAConfirmarse() {
        ClassAttendance cancelled = ClassAttendance.createPending(1L, 2L).cancel();

        ClassAttendance result = cancelled.confirm();

        assertThat(result.status()).isEqualTo(ClassAttendanceStatus.CONFIRMED);
    }

    // ─── cancel() ───────────────────────────────────────────────────────────────

    @Test
    void cancel_pendiente_pasaACancelled() {
        ClassAttendance result = ClassAttendance.createPending(1L, 2L).cancel();

        assertThat(result.status()).isEqualTo(ClassAttendanceStatus.CANCELLED);
    }

    @Test
    void cancel_yaCancelada_siguePermitiendoloYQuedaCancelled() {
        ClassAttendance cancelled = ClassAttendance.createPending(1L, 2L).cancel();

        ClassAttendance result = cancelled.cancel();

        assertThat(result.status()).isEqualTo(ClassAttendanceStatus.CANCELLED);
    }

    @Test
    void cancel_confirmada_puedeCancelarseIgual() {
        ClassAttendance confirmed = ClassAttendance.createPending(1L, 2L).confirm();

        ClassAttendance result = confirmed.cancel();

        assertThat(result.status()).isEqualTo(ClassAttendanceStatus.CANCELLED);
    }

    // ─── cancelar NO afecta identidad de sesión/alumno (no es un ClassEnrollment) ──

    @Test
    void cancel_noModificaClassSessionIdNiPlayerId() {
        ClassAttendance original = ClassAttendance.createPending(1L, 2L);

        ClassAttendance result = original.cancel();

        assertThat(result.classSessionId()).isEqualTo(original.classSessionId());
        assertThat(result.playerId()).isEqualTo(original.playerId());
    }

    // ─── belongsTo() — preparado para autoservicio futuro del Player ────────────

    @Test
    void belongsTo_mismoPlayer_esTrue() {
        assertThat(ClassAttendance.createPending(1L, 2L).belongsTo(2L)).isTrue();
    }

    @Test
    void belongsTo_otroPlayer_esFalse() {
        assertThat(ClassAttendance.createPending(1L, 2L).belongsTo(99L)).isFalse();
    }

    // ─── withId() ───────────────────────────────────────────────────────────────

    @Test
    void withId_preservaTodosLosCamposExceptoId() {
        ClassAttendance original = ClassAttendance.createPending(1L, 2L);
        ClassAttendance result = original.withId(33L);

        assertThat(result.id()).isEqualTo(33L);
        assertThat(result.classSessionId()).isEqualTo(original.classSessionId());
        assertThat(result.playerId()).isEqualTo(original.playerId());
        assertThat(result.status()).isEqualTo(original.status());
    }
}
