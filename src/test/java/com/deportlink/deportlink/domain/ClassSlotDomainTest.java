package com.deportlink.deportlink.domain;

import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.InvalidCapacityException;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;
import com.deportlink.deportlink.exception.StatusAlreadyAppliedException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

class ClassSlotDomainTest {

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static ClassSlot slot(int capacity) {
        return ClassSlot.create(1L, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, capacity);
    }

    // ─── create() — caso válido ─────────────────────────────────────────────────

    @Test
    void create_datosValidos_naceActivoConLosDatosProvistos() {
        ClassSlot result = slot(4);

        assertThat(result.id()).isNull();
        assertThat(result.instructorId()).isEqualTo(1L);
        assertThat(result.courtId()).isEqualTo(2L);
        assertThat(result.dayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
        assertThat(result.startTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(result.duration()).isEqualTo(Duration.ofHours(1));
        assertThat(result.level()).isEqualTo(Level.INTERMEDIO);
        assertThat(result.capacity()).isEqualTo(4);
        assertThat(result.status()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(result.isActive()).isTrue();
    }

    // ─── create() — Level: valores válidos ──────────────────────────────────────

    @Test
    void create_aceptaLosTresNivelesDelNegocio() {
        assertThat(ClassSlot.create(1L, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.PRINCIPIANTE, 4).level()).isEqualTo(Level.PRINCIPIANTE);
        assertThat(ClassSlot.create(1L, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4).level()).isEqualTo(Level.INTERMEDIO);
        assertThat(ClassSlot.create(1L, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.AVANZADO, 4).level()).isEqualTo(Level.AVANZADO);
    }

    // ─── create() — capacidad ───────────────────────────────────────────────────

    @Test
    void create_capacidad1_esValida() {
        assertThat(slot(1).capacity()).isEqualTo(1);
    }

    @Test
    void create_capacidad4_esValida() {
        assertThat(slot(4).capacity()).isEqualTo(4);
    }

    @Test
    void create_capacidadCero_lanzaInvalidCapacity() {
        assertThatThrownBy(() -> slot(0)).isInstanceOf(InvalidCapacityException.class);
    }

    @Test
    void create_capacidadNegativa_lanzaInvalidCapacity() {
        assertThatThrownBy(() -> slot(-1)).isInstanceOf(InvalidCapacityException.class);
    }

    @Test
    void create_capacidadMayorAlMaximoDelNegocio_lanzaInvalidCapacity() {
        assertThatThrownBy(() -> slot(ClassSlot.MAX_CAPACITY + 1))
                .isInstanceOf(InvalidCapacityException.class);
    }

    // ─── create() — duración ────────────────────────────────────────────────────

    @Test
    void create_duracionCero_lanzaInvalidTimeRange() {
        assertThatThrownBy(() -> ClassSlot.create(1L, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ZERO, Level.INTERMEDIO, 4))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    @Test
    void create_duracionNegativa_lanzaInvalidTimeRange() {
        assertThatThrownBy(() -> ClassSlot.create(1L, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofMinutes(-30), Level.INTERMEDIO, 4))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    // ─── create() — datos obligatorios ──────────────────────────────────────────

    @Test
    void create_instructorNulo_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassSlot.create(null, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_canchaNula_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassSlot.create(1L, null, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_diaDeSemanaNulo_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassSlot.create(1L, 2L, null, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_horaDeInicioNula_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassSlot.create(1L, 2L, DayOfWeek.THURSDAY, null,
                Duration.ofHours(1), Level.INTERMEDIO, 4))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_duracionNula_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassSlot.create(1L, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                null, Level.INTERMEDIO, 4))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_nivelNulo_lanzaNullPointerException() {
        assertThatThrownBy(() -> ClassSlot.create(1L, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), null, 4))
                .isInstanceOf(NullPointerException.class);
    }

    // ─── pause() / reactivate() ─────────────────────────────────────────────────

    @Test
    void pause_slotActivo_pasaAInactivo() {
        ClassSlot result = slot(4).pause();

        assertThat(result.status()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(result.isActive()).isFalse();
    }

    @Test
    void pause_slotYaPausado_lanzaStatusAlreadyApplied() {
        ClassSlot paused = slot(4).pause();

        assertThatThrownBy(paused::pause).isInstanceOf(StatusAlreadyAppliedException.class);
    }

    @Test
    void reactivate_slotPausado_pasaAActivo() {
        ClassSlot paused = slot(4).pause();

        ClassSlot result = paused.reactivate();

        assertThat(result.status()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void reactivate_slotYaActivo_lanzaStatusAlreadyApplied() {
        ClassSlot active = slot(4);

        assertThatThrownBy(active::reactivate).isInstanceOf(StatusAlreadyAppliedException.class);
    }

    @Test
    void pauseYReactivate_preservanElRestoDeLosDatos() {
        ClassSlot original = slot(4);

        ClassSlot result = original.pause().reactivate();

        assertThat(result.instructorId()).isEqualTo(original.instructorId());
        assertThat(result.courtId()).isEqualTo(original.courtId());
        assertThat(result.dayOfWeek()).isEqualTo(original.dayOfWeek());
        assertThat(result.startTime()).isEqualTo(original.startTime());
        assertThat(result.duration()).isEqualTo(original.duration());
        assertThat(result.level()).isEqualTo(original.level());
        assertThat(result.capacity()).isEqualTo(original.capacity());
    }

    // ─── hasRoom() — regla de capacidad verificable en el dominio ──────────────

    @Test
    void hasRoom_conteoMenorQueCapacidad_esTrue() {
        assertThat(slot(4).hasRoom(3)).isTrue();
    }

    @Test
    void hasRoom_conteoIgualACapacidad_esFalse() {
        assertThat(slot(4).hasRoom(4)).isFalse();
    }

    @Test
    void hasRoom_conteoMayorACapacidad_esFalse() {
        assertThat(slot(4).hasRoom(5)).isFalse();
    }

    // ─── withId() ───────────────────────────────────────────────────────────────

    @Test
    void withId_preservaTodosLosCamposExceptoId() {
        ClassSlot original = slot(4);
        ClassSlot result = original.withId(99L);

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.instructorId()).isEqualTo(original.instructorId());
        assertThat(result.courtId()).isEqualTo(original.courtId());
        assertThat(result.status()).isEqualTo(original.status());
    }
}
