package com.deportlink.deportlink.usecase.classattendance;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.usecase.classattendance.CancelAttendanceUseCase;
import com.deportlink.deportlink.domain.model.ClassAttendance;
import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSessionRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.ClassAttendanceStatus;
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
class CancelAttendanceUseCaseTest {

    @Mock private ClassAttendanceRepositoryPort classAttendanceRepository;
    @Mock private ClassSessionRepositoryPort classSessionRepository;
    @Mock private ClassSlotRepositoryPort classSlotRepository;

    @InjectMocks private CancelAttendanceUseCase useCase;

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
        when(classAttendanceRepository.findById(ATTENDANCE_ID)).thenReturn(Optional.of(attendance));
        when(classSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session(sessionStatus)));
        when(classSlotRepository.findByAttendanceIdForUpdate(ATTENDANCE_ID)).thenReturn(Optional.of(slot()));
    }

    @Test
    void execute_pendiente_pasaACancelled() {
        ClassAttendance pending = ClassAttendance.createPending(SESSION_ID, PLAYER_ID).withId(ATTENDANCE_ID);
        stubResolutionChain(pending, ClassSessionStatus.SCHEDULED);
        when(classAttendanceRepository.save(any(ClassAttendance.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassAttendance result = useCase.execute(ownerInstructor(), ATTENDANCE_ID);

        assertThat(result.status()).isEqualTo(ClassAttendanceStatus.CANCELLED);
    }

    @Test
    void execute_yaCancelada_esIdempotenteYNoLanza() {
        ClassAttendance cancelled = ClassAttendance.createPending(SESSION_ID, PLAYER_ID).cancel().withId(ATTENDANCE_ID);
        stubResolutionChain(cancelled, ClassSessionStatus.SCHEDULED);
        when(classAttendanceRepository.save(any(ClassAttendance.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassAttendance result = useCase.execute(ownerInstructor(), ATTENDANCE_ID);

        assertThat(result.status()).isEqualTo(ClassAttendanceStatus.CANCELLED);
    }

    @Test
    void execute_confirmada_puedeCancelarseIgual() {
        ClassAttendance confirmed = ClassAttendance.createPending(SESSION_ID, PLAYER_ID).confirm().withId(ATTENDANCE_ID);
        stubResolutionChain(confirmed, ClassSessionStatus.SCHEDULED);
        when(classAttendanceRepository.save(any(ClassAttendance.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassAttendance result = useCase.execute(ownerInstructor(), ATTENDANCE_ID);

        assertThat(result.status()).isEqualTo(ClassAttendanceStatus.CANCELLED);
    }

    @Test
    void execute_noModificaClassEnrollment() {
        // La cancelación de asistencia sí modifica el status guardado, pero nunca toca playerId
        // ni classSessionId — no hay ninguna dependencia de ClassEnrollment en este caso de uso.
        ClassAttendance pending = ClassAttendance.createPending(SESSION_ID, PLAYER_ID).withId(ATTENDANCE_ID);
        stubResolutionChain(pending, ClassSessionStatus.SCHEDULED);
        when(classAttendanceRepository.save(any(ClassAttendance.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassAttendance result = useCase.execute(ownerInstructor(), ATTENDANCE_ID);

        assertThat(result.playerId()).isEqualTo(PLAYER_ID);
        assertThat(result.classSessionId()).isEqualTo(SESSION_ID);
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
}

