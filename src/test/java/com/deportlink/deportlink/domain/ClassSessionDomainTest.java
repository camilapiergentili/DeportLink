package com.deportlink.deportlink.domain;

import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.enums.ClassSessionStatus;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

class ClassSessionDomainTest {

    private static final LocalDate FUTURE_DAY = LocalDate.now().plusDays(7);
    private static final LocalDate PAST_DAY = LocalDate.now().minusDays(7);

    // ─── create() — caso válido ─────────────────────────────────────────────────

    @Test
    void create_datosValidos_naceScheduledConLosDatosProvistos() {
        ClassSession result = ClassSession.create(1L, FUTURE_DAY, LocalTime.of(15, 0), Duration.ofHours(1));

        assertThat(result.id()).isNull();
        assertThat(result.classSlotId()).isEqualTo(1L);
        assertThat(result.day()).isEqualTo(FUTURE_DAY);
        assertThat(result.startTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(result.duration()).isEqualTo(Duration.ofHours(1));
        assertThat(result.status()).isEqualTo(ClassSessionStatus.SCHEDULED);
        assertThat(result.isScheduled()).isTrue();
    }

    // ─── create() — fecha válida ────────────────────────────────────────────────

    @Test
    void create_fechaPasada_lanzaInvalidTimeRange() {
        assertThatThrownBy(() -> ClassSession.create(1L, PAST_DAY, LocalTime.of(15, 0), Duration.ofHours(1)))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    @Test
    void create_hoyPeroHoraYaPasada_lanzaInvalidTimeRange() {
        // Fecha y hora se calculan JUNTAS: restar una hora solo a la LocalTime y combinarla con
        // LocalDate.now() da un instante futuro entre las 00:00 y las 00:59 (23:07 "de hoy" a las
        // 00:07), y el test dejaba de fallar cuando debía. Sigue sin usar un reloj inyectable, igual
        // que la validación que ejerce en ClassSession.create.
        LocalDateTime anHourAgo = LocalDateTime.now().minusHours(1);
        assertThatThrownBy(() -> ClassSession.create(1L, anHourAgo.toLocalDate(), anHourAgo.toLocalTime(), Duration.ofHours(1)))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    // ─── create() — horario / duración válidos ──────────────────────────────────

    @Test
    void create_duracionCero_lanzaInvalidTimeRange() {
        assertThatThrownBy(() -> ClassSession.create(1L, FUTURE_DAY, LocalTime.of(15, 0), Duration.ZERO))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    @Test
    void create_duracionNegativa_lanzaInvalidTimeRange() {
        assertThatThrownBy(() -> ClassSession.create(1L, FUTURE_DAY, LocalTime.of(15, 0), Duration.ofMinutes(-1)))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    // ─── create() — classSlot obligatorio ───────────────────────────────────────

    @Test
    void create_classSlotNulo_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassSession.create(null, FUTURE_DAY, LocalTime.of(15, 0), Duration.ofHours(1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_fechaNula_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassSession.create(1L, null, LocalTime.of(15, 0), Duration.ofHours(1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_horaDeInicioNula_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassSession.create(1L, FUTURE_DAY, null, Duration.ofHours(1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_duracionNula_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassSession.create(1L, FUTURE_DAY, LocalTime.of(15, 0), null))
                .isInstanceOf(NullPointerException.class);
    }

    // ─── withId() ───────────────────────────────────────────────────────────────

    @Test
    void withId_preservaTodosLosCamposExceptoId() {
        ClassSession original = ClassSession.create(1L, FUTURE_DAY, LocalTime.of(15, 0), Duration.ofHours(1));
        ClassSession result = original.withId(77L);

        assertThat(result.id()).isEqualTo(77L);
        assertThat(result.classSlotId()).isEqualTo(original.classSlotId());
        assertThat(result.day()).isEqualTo(original.day());
        assertThat(result.startTime()).isEqualTo(original.startTime());
        assertThat(result.duration()).isEqualTo(original.duration());
        assertThat(result.status()).isEqualTo(original.status());
    }
}
