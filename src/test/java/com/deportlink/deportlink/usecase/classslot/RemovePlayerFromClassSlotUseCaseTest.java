package com.deportlink.deportlink.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.usecase.classslot.RemovePlayerFromClassSlotUseCase;
import com.deportlink.deportlink.domain.model.ClassEnrollment;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassEnrollmentRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.ClassEnrollmentNotFoundException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemovePlayerFromClassSlotUseCaseTest {
    @Mock private com.deportlink.deportlink.application.usecase.classattendance.SyncFutureClassRoster syncFutureRoster;

    @Mock private ClassSlotRepositoryPort classSlotRepository;
    @Mock private ClassEnrollmentRepositoryPort classEnrollmentRepository;

    @InjectMocks private RemovePlayerFromClassSlotUseCase useCase;

    private static final Long SLOT_ID = 1L;
    private static final Long INSTRUCTOR_ID = 10L;
    private static final Long PLAYER_ID = 20L;

    private static ClassSlot slot() {
        return new ClassSlot(SLOT_ID, INSTRUCTOR_ID, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4, ActiveStatus.ACTIVE);
    }

    private static Actor ownerInstructor() { return new Actor(INSTRUCTOR_ID, ActorRole.INSTRUCTOR); }
    private static Actor otherInstructor() { return new Actor(999L, ActorRole.INSTRUCTOR); }

    @Test
    void execute_alumnoActivo_lodaDeBajaLogicamente() {
        ClassEnrollment active = new ClassEnrollment(5L, SLOT_ID, PLAYER_ID, true);
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot()));
        when(classEnrollmentRepository.findByClassSlotIdAndPlayerId(SLOT_ID, PLAYER_ID)).thenReturn(Optional.of(active));
        when(classEnrollmentRepository.save(any(ClassEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassEnrollment result = useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID);

        assertThat(result.active()).isFalse();
        assertThat(result.id()).isEqualTo(5L);
    }

    @Test
    void execute_instructorAjeno_lanzaClassSlotNotFoundSinTocarElEnrollment() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot()));

        assertThatThrownBy(() -> useCase.execute(otherInstructor(), SLOT_ID, PLAYER_ID))
                .isInstanceOf(ClassSlotNotFoundException.class);

        verifyNoInteractions(classEnrollmentRepository);
    }

    @Test
    void execute_slotInexistente_lanzaClassSlotNotFound() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID))
                .isInstanceOf(ClassSlotNotFoundException.class);

        verifyNoInteractions(classEnrollmentRepository);
    }

    @Test
    void execute_alumnoNuncaPertenecio_lanzaClassEnrollmentNotFound() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot()));
        when(classEnrollmentRepository.findByClassSlotIdAndPlayerId(SLOT_ID, PLAYER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID))
                .isInstanceOf(ClassEnrollmentNotFoundException.class);

        verify(classEnrollmentRepository, never()).save(any());
    }

    @Test
    void execute_alumnoYaInactivo_propagaStatusAlreadyAppliedDelDominio() {
        ClassEnrollment inactive = new ClassEnrollment(5L, SLOT_ID, PLAYER_ID, false);
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot()));
        when(classEnrollmentRepository.findByClassSlotIdAndPlayerId(SLOT_ID, PLAYER_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID))
                .isInstanceOf(StatusAlreadyAppliedException.class);

        verify(classEnrollmentRepository, never()).save(any());
    }
}
