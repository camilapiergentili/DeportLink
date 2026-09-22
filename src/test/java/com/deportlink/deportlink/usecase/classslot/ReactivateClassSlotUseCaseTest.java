package com.deportlink.deportlink.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.usecase.classslot.ReactivateClassSlotUseCase;
import com.deportlink.deportlink.application.usecase.classslot.ValidateClassSlotSchedule;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import com.deportlink.deportlink.exception.ClassSlotScheduleMismatchException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import com.deportlink.deportlink.exception.StatusAlreadyAppliedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactivateClassSlotUseCaseTest {
    @Mock private com.deportlink.deportlink.application.port.out.ClassSlotCourtGateway classSlotCourtGateway;
    @Mock private com.deportlink.deportlink.application.usecase.classslot.ValidateClassRecurrence validateRecurrence;
    @Mock private ValidateClassSlotSchedule validateSchedule;
    @Mock private com.deportlink.deportlink.application.usecase.classsession.MaintainClassSlotScheduleUseCase maintainSchedule;
    @org.junit.jupiter.api.BeforeEach
    void lockCourt() {
        when(classSlotCourtGateway.findCourtIdByClassSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(2L));
    }

    @Mock private ClassSlotRepositoryPort classSlotRepository;
    @InjectMocks private ReactivateClassSlotUseCase useCase;

    private static final Long SLOT_ID = 1L;
    private static final Long INSTRUCTOR_ID = 10L;
    private static final Long COURT_ID = 2L;

    private static ClassSlot slot(ActiveStatus status) {
        return new ClassSlot(SLOT_ID, INSTRUCTOR_ID, COURT_ID, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4, status);
    }

    private static Actor ownerInstructor() { return new Actor(INSTRUCTOR_ID, ActorRole.INSTRUCTOR); }
    private static Actor otherInstructor() { return new Actor(999L, ActorRole.INSTRUCTOR); }

    @Test
    void execute_slotPausado_loReactivaYGuarda() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot(ActiveStatus.INACTIVE)));
        // validateSchedule/validateRecurrence son void — no lanzar es el default de Mockito, mismo
        // criterio ya usado para validateRecurrence antes de este fix.
        when(classSlotRepository.save(any(ClassSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassSlot result = useCase.execute(ownerInstructor(), SLOT_ID);

        assertThat(result.status()).isEqualTo(ActiveStatus.ACTIVE);
        verify(classSlotRepository).save(any(ClassSlot.class));
        // F17: validateSchedule debe correr antes que validateRecurrence y antes de save — ver
        // docs/loop/F17.md, IMPLEMENT.
        InOrder order = inOrder(validateSchedule, validateRecurrence, classSlotRepository);
        order.verify(validateSchedule).execute(any(ClassSlot.class));
        order.verify(validateRecurrence).execute(any(ClassSlot.class));
        order.verify(classSlotRepository).save(any(ClassSlot.class));
    }

    @Test
    void execute_slotInexistente_lanzaClassSlotNotFound() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID))
                .isInstanceOf(ClassSlotNotFoundException.class);

        verify(classSlotRepository, never()).save(any());
    }

    @Test
    void execute_instructorAjeno_lanzaClassSlotNotFoundSinGuardar() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot(ActiveStatus.INACTIVE)));

        assertThatThrownBy(() -> useCase.execute(otherInstructor(), SLOT_ID))
                .isInstanceOf(ClassSlotNotFoundException.class);

        verify(classSlotRepository, never()).save(any());
    }

    @Test
    void execute_slotYaActivo_propagaStatusAlreadyAppliedDelDominio() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot(ActiveStatus.ACTIVE)));

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID))
                .isInstanceOf(StatusAlreadyAppliedException.class);

        verify(classSlotRepository, never()).save(any());
    }

    // ─── F17 — re-validación de la grilla de Schedule al reactivar (docs/loop/F17.md) ────
    // La lógica de qué cuenta como "alineado" la prueba ValidateClassSlotScheduleTest de forma
    // aislada; acá solo se verifica que este caso de uso invoca a ese validador antes de
    // validateRecurrence/save, y propaga lo que sea que lance.

    @Test
    void execute_agendaInexistenteAlReactivar_propagaScheduleNotFoundSinGuardar() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot(ActiveStatus.INACTIVE)));
        doThrow(new ScheduleNotFoundException("No hay agenda disponible para ese día en esta cancha"))
                .when(validateSchedule).execute(any(ClassSlot.class));

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID))
                .isInstanceOf(ScheduleNotFoundException.class);

        verify(classSlotRepository, never()).save(any());
        verifyNoInteractions(validateRecurrence);
    }

    @Test
    void execute_agendaYaNoCoincideAlReactivar_propagaClassSlotScheduleMismatchSinGuardar() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot(ActiveStatus.INACTIVE)));
        // Simula que, mientras el slot estaba pausado, la agenda de ese día cambió (se borró y
        // recreó con otra grilla) y el slot (15:00, 1h) ya no coincide.
        doThrow(new ClassSlotScheduleMismatchException("no coincide con la grilla"))
                .when(validateSchedule).execute(any(ClassSlot.class));

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID))
                .isInstanceOf(ClassSlotScheduleMismatchException.class);

        verify(classSlotRepository, never()).save(any());
        verifyNoInteractions(validateRecurrence);
    }
}
