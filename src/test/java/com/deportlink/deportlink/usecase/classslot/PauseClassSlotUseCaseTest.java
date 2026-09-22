package com.deportlink.deportlink.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.usecase.classslot.PauseClassSlotUseCase;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import com.deportlink.deportlink.exception.StatusAlreadyAppliedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PauseClassSlotUseCaseTest {

    @Mock private ClassSlotRepositoryPort classSlotRepository;
    @InjectMocks private PauseClassSlotUseCase useCase;

    private static final Long SLOT_ID = 1L;
    private static final Long INSTRUCTOR_ID = 10L;

    private static ClassSlot activeSlot(ActiveStatus status) {
        return new ClassSlot(SLOT_ID, INSTRUCTOR_ID, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4, status);
    }

    private static Actor ownerInstructor() { return new Actor(INSTRUCTOR_ID, ActorRole.INSTRUCTOR); }
    private static Actor otherInstructor() { return new Actor(999L, ActorRole.INSTRUCTOR); }
    private static Actor admin() { return new Actor(999L, ActorRole.ADMIN); }

    @Test
    void execute_slotActivoDelInstructorDueño_loPausaYGuarda() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot(ActiveStatus.ACTIVE)));
        when(classSlotRepository.save(any(ClassSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassSlot result = useCase.execute(ownerInstructor(), SLOT_ID);

        assertThat(result.status()).isEqualTo(ActiveStatus.INACTIVE);
        verify(classSlotRepository).save(any(ClassSlot.class));
    }

    @Test
    void execute_adminPuedePausarSlotDeCualquierInstructor() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot(ActiveStatus.ACTIVE)));
        when(classSlotRepository.save(any(ClassSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassSlot result = useCase.execute(admin(), SLOT_ID);

        assertThat(result.status()).isEqualTo(ActiveStatus.INACTIVE);
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
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot(ActiveStatus.ACTIVE)));

        assertThatThrownBy(() -> useCase.execute(otherInstructor(), SLOT_ID))
                .isInstanceOf(ClassSlotNotFoundException.class);

        verify(classSlotRepository, never()).save(any());
    }

    @Test
    void execute_slotYaPausado_propagaStatusAlreadyAppliedDelDominio() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot(ActiveStatus.INACTIVE)));

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID))
                .isInstanceOf(StatusAlreadyAppliedException.class);

        verify(classSlotRepository, never()).save(any());
    }
}
