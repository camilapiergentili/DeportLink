package com.deportlink.deportlink.usecase.classslot;

import com.deportlink.deportlink.application.port.out.ScheduleGateway;
import com.deportlink.deportlink.application.port.out.ScheduleGateway.SlotConfig;
import com.deportlink.deportlink.application.usecase.classslot.ValidateClassSlotSchedule;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.ClassSlotScheduleMismatchException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Ver docs/loop/F17.md — cierra F17 (docs/software-review-2026-09-09.md): un {@link ClassSlot} debe
 * coincidir exactamente (mismo startTime alineado a la grilla, misma duración) con la agenda
 * ({@code Schedule}) de su cancha, o la propiedad "coincidencia exacta ⟺ solapamiento real" de la
 * que depende {@code court_occupancy}/{@code ClassRecurrencePort} deja de valer.
 */
@ExtendWith(MockitoExtension.class)
class ValidateClassSlotScheduleTest {

    @Mock private ScheduleGateway scheduleGateway;
    @InjectMocks private ValidateClassSlotSchedule validator;

    private static final Long COURT_ID = 2L;

    private static ClassSlot slot(LocalTime startTime, Duration duration) {
        return new ClassSlot(1L, 10L, COURT_ID, DayOfWeek.THURSDAY, startTime, duration,
                Level.INTERMEDIO, 4, ActiveStatus.ACTIVE);
    }

    private static SlotConfig grid(LocalTime opening, LocalTime closing, Duration slotDuration) {
        return new SlotConfig(opening, closing, slotDuration);
    }

    @Test
    void execute_sinAgendaParaEseDia_lanzaScheduleNotFound() {
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DayOfWeek.THURSDAY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.execute(slot(LocalTime.of(15, 0), Duration.ofHours(1))))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void execute_horarioNoAlineadoALaGrilla_lanzaClassSlotScheduleMismatch() {
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DayOfWeek.THURSDAY))
                .thenReturn(Optional.of(grid(LocalTime.of(8, 0), LocalTime.of(20, 0), Duration.ofHours(1))));
        // 15:30 no es múltiplo de 1h desde las 08:00.

        assertThatThrownBy(() -> validator.execute(slot(LocalTime.of(15, 30), Duration.ofHours(1))))
                .isInstanceOf(ClassSlotScheduleMismatchException.class);
    }

    @Test
    void execute_duracionDistintaDeLaGrillaAunqueElInicioCoincida_lanzaClassSlotScheduleMismatch() {
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DayOfWeek.THURSDAY))
                .thenReturn(Optional.of(grid(LocalTime.of(8, 0), LocalTime.of(20, 0), Duration.ofHours(1))));
        // 15:00 sí es un turno válido, pero la duración de 2h no coincide con la grilla de 1h —
        // exactamente el escenario que F17 reporta: una clase de 15:00-17:00 solapa físicamente el
        // turno de 16:00 sin que ninguna coincidencia exacta de start_time lo detecte.

        assertThatThrownBy(() -> validator.execute(slot(LocalTime.of(15, 0), Duration.ofHours(2))))
                .isInstanceOf(ClassSlotScheduleMismatchException.class);
    }

    @Test
    void execute_duracionMenorQueLaGrillaAunqueElInicioCoincida_lanzaClassSlotScheduleMismatch() {
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DayOfWeek.THURSDAY))
                .thenReturn(Optional.of(grid(LocalTime.of(8, 0), LocalTime.of(20, 0), Duration.ofHours(1))));

        assertThatThrownBy(() -> validator.execute(slot(LocalTime.of(15, 0), Duration.ofMinutes(45))))
                .isInstanceOf(ClassSlotScheduleMismatchException.class);
    }

    @Test
    void execute_horarioYDuracionAlineados_noLanza() {
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DayOfWeek.THURSDAY))
                .thenReturn(Optional.of(grid(LocalTime.of(8, 0), LocalTime.of(20, 0), Duration.ofHours(1))));

        assertThatCode(() -> validator.execute(slot(LocalTime.of(15, 0), Duration.ofHours(1))))
                .doesNotThrowAnyException();
    }
}
