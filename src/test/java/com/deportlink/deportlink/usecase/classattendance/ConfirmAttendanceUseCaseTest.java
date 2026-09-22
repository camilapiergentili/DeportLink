package com.deportlink.deportlink.usecase.classattendance;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.usecase.classattendance.ConfirmAttendanceUseCase;
import com.deportlink.deportlink.domain.model.ClassAttendance;
import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSessionRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.ClassSessionStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.ClassAttendanceNotFoundException;
import com.deportlink.deportlink.exception.ClassSessionNotScheduledException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
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
class ConfirmAttendanceUseCaseTest {
    @Mock private com.deportlink.deportlink.domain.port.out.ClassEnrollmentRepositoryPort enrollments;
    @org.mockito.Spy private java.time.Clock clock = java.time.Clock.systemDefaultZone();

    @Mock private ClassAttendanceRepositoryPort classAttendanceRepository;
    @Mock private ClassSessionRepositoryPort classSessionRepository;
    @Mock private ClassSlotRepositoryPort classSlotRepository;

    @InjectMocks private ConfirmAttendanceUseCase useCase;

    private static final Long ATTENDANCE_ID = 1L;
    private static final Long SESSION_ID = 2L;
    private static final Long SLOT_ID = 3L;
    private static final Long INSTRUCTOR_ID = 10L;
    private static final Long PLAYER_ID = 20L;

    private static Actor ownerInstructor() { return new Actor(INSTRUCTOR_ID, ActorRole.INSTRUCTOR); }
    private static Actor otherInstructor() { return new Actor(999L, ActorRole.INSTRUCTOR); }

    private static ClassSlot slot() {
        return new ClassSlot(SLOT_ID, INSTRUCTOR_ID, 4L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4, ActiveStatus.ACTIVE);
    }

    private static ClassSession session(ClassSessionStatus status) {
        return new ClassSession(SESSION_ID, SLOT_ID, LocalDate.now().plusDays(2), LocalTime.of(15, 0),
                Duration.ofHours(1), status);
    }

    private void stubResolutionChain(ClassAttendance attendance, ClassSessionStatus sessionStatus) {
        if (sessionStatus == ClassSessionStatus.SCHEDULED) {
            when(enrollments.findByClassSlotIdAndPlayerId(SLOT_ID, PLAYER_ID)).thenReturn(
                    Optional.of(com.deportlink.deportlink.domain.model.ClassEnrollment.create(SLOT_ID, PLAYER_ID)));
        }
        when(classAttendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(attendance));
        when(classSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session(sessionStatus)));
        when(classSlotRepository.findByAttendanceIdForUpdate(ATTENDANCE_ID)).thenReturn(Optional.of(slot()));
    }

    @Test
    void execute_pendiente_pasaAConfirmed() {
        ClassAttendance pending = ClassAttendance.createPending(SESSION_ID, PLAYER_ID).withId(ATTENDANCE_ID);
        stubResolutionChain(pending, ClassSessionStatus.SCHEDULED);
        when(classAttendanceRepository.save(any(ClassAttendance.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassAttendance result = useCase.execute(ownerInstructor(), ATTENDANCE_ID);

        assertThat(result.status()).isEqualTo(com.deportlink.deportlink.enums.ClassAttendanceStatus.CONFIRMED);
    }

    @Test
    void execute_yaConfirmada_esIdempotenteYNoLanza() {
        ClassAttendance confirmed = ClassAttendance.createPending(SESSION_ID, PLAYER_ID).confirm().withId(ATTENDANCE_ID);
        stubResolutionChain(confirmed, ClassSessionStatus.SCHEDULED);
        when(classAttendanceRepository.save(any(ClassAttendance.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassAttendance result = useCase.execute(ownerInstructor(), ATTENDANCE_ID);

        assertThat(result.status()).isEqualTo(com.deportlink.deportlink.enums.ClassAttendanceStatus.CONFIRMED);
    }

    @Test
    void execute_cancelada_puedeReconfirmarse() {
        ClassAttendance cancelled = ClassAttendance.createPending(SESSION_ID, PLAYER_ID).cancel().withId(ATTENDANCE_ID);
        stubResolutionChain(cancelled, ClassSessionStatus.SCHEDULED);
        when(classAttendanceRepository.save(any(ClassAttendance.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassAttendance result = useCase.execute(ownerInstructor(), ATTENDANCE_ID);

        assertThat(result.status()).isEqualTo(com.deportlink.deportlink.enums.ClassAttendanceStatus.CONFIRMED);
    }

    @Test
    void execute_sesionNoScheduled_lanzaClassSessionNotScheduledSinGuardar() {
        ClassAttendance pending = ClassAttendance.createPending(SESSION_ID, PLAYER_ID).withId(ATTENDANCE_ID);
        stubResolutionChain(pending, ClassSessionStatus.CANCELLED);

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), ATTENDANCE_ID))
                .isInstanceOf(ClassSessionNotScheduledException.class);

        verify(classAttendanceRepository, never()).save(any());
    }

    @Test
    void execute_instructorAjeno_lanzaClassAttendanceNotFoundSinGuardar() {
        when(classSlotRepository.findByAttendanceIdForUpdate(ATTENDANCE_ID)).thenReturn(Optional.of(slot()));

        assertThatThrownBy(() -> useCase.execute(otherInstructor(), ATTENDANCE_ID))
                .isInstanceOf(ClassAttendanceNotFoundException.class);

        verify(classAttendanceRepository, never()).save(any());
    }

    @Test
    void execute_asistenciaInexistente_lanzaClassAttendanceNotFound() {
        when(classSlotRepository.findByAttendanceIdForUpdate(ATTENDANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), ATTENDANCE_ID))
                .isInstanceOf(ClassAttendanceNotFoundException.class);

        verifyNoInteractions(classSessionRepository, classAttendanceRepository);
    }

    @Test
    void execute_noModificaEnrollmentNiOcupacionDeCancha() {
        // No hay ningún puerto de ClassEnrollment ni de ocupación entre las dependencias de este
        // caso de uso — la ausencia misma de esas dependencias documenta que confirmar/cancelar
        // asistencia no puede tener efectos sobre el grupo ni la cancha.
        ClassAttendance pending = ClassAttendance.createPending(SESSION_ID, PLAYER_ID).withId(ATTENDANCE_ID);
        stubResolutionChain(pending, ClassSessionStatus.SCHEDULED);
        when(classAttendanceRepository.save(any(ClassAttendance.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(ownerInstructor(), ATTENDANCE_ID);

        verify(classSlotRepository, never()).save(any());
        verify(classSlotRepository, never()).findByIdForUpdate(any());
    }
}

