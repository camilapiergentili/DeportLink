package com.deportlink.deportlink.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.port.out.InstructorGateway;
import com.deportlink.deportlink.application.port.out.InstructorGateway.InstructorSnapshot;
import com.deportlink.deportlink.application.usecase.classslot.CreateClassSlotCommand;
import com.deportlink.deportlink.application.usecase.classslot.CreateClassSlotUseCase;
import com.deportlink.deportlink.application.usecase.classslot.ValidateClassSlotSchedule;
import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.BranchNotFoundException;
import com.deportlink.deportlink.exception.ClassSlotScheduleMismatchException;
import com.deportlink.deportlink.exception.CourtNotActiveException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.InstructorNotFoundException;
import com.deportlink.deportlink.exception.InvalidCapacityException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
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
class CreateClassSlotUseCaseTest {
    @Mock private com.deportlink.deportlink.application.usecase.classslot.ValidateClassRecurrence validateRecurrence;
    @Mock private ValidateClassSlotSchedule validateSchedule;
    @Mock private com.deportlink.deportlink.application.usecase.classsession.MaintainClassSlotScheduleUseCase maintainSchedule;

    @Mock private InstructorGateway instructorGateway;
    @Mock private CourtRepositoryPort courtRepository;
    @Mock private BranchRepositoryPort branchRepository;
    @Mock private ClassSlotRepositoryPort classSlotRepository;

    @InjectMocks private CreateClassSlotUseCase useCase;

    private static final Long INSTRUCTOR_ID = 1L;
    private static final Long COURT_ID = 2L;
    private static final Long BRANCH_ID = 3L;

    private static Actor instructorActor() { return new Actor(INSTRUCTOR_ID, ActorRole.INSTRUCTOR); }
    private static Actor adminActor() { return new Actor(999L, ActorRole.ADMIN); }

    private static CreateClassSlotCommand command(Long instructorId) {
        return new CreateClassSlotCommand(instructorId, COURT_ID, DayOfWeek.THURSDAY,
                LocalTime.of(15, 0), Duration.ofHours(1), Level.INTERMEDIO, 4);
    }

    private static Court activeCourt() {
        return new Court(COURT_ID, "Cancha 1", 1000.0, BRANCH_ID, 1L, "Pádel", ActiveStatus.ACTIVE);
    }

    private static Branch approvedBranch() {
        Address address = new Address("Calle Falsa", 123, "CABA", "Buenos Aires", 1000, -34.6, -58.4);
        return new Branch(BRANCH_ID, "Sucursal 1", address, 10L, VerificationStatus.APPROVED, ActiveStatus.ACTIVE, 12);
    }

    private void stubHappyPathDependencies(Long instructorId) {
        when(instructorGateway.findById(instructorId)).thenReturn(Optional.of(new InstructorSnapshot(instructorId)));
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        // validateSchedule/validateRecurrence son void — no lanzar es el default de Mockito, mismo
        // criterio ya usado para validateRecurrence antes de este fix.
        when(classSlotRepository.save(any(ClassSlot.class))).thenAnswer(inv -> inv.getArgument(0, ClassSlot.class).withId(50L));
    }

    // ─── Camino exitoso ─────────────────────────────────────────────────────────

    @Test
    void execute_instructorCreaParaSiMismo_guardaYDevuelveElClassSlotConId() {
        stubHappyPathDependencies(INSTRUCTOR_ID);

        ClassSlot result = useCase.execute(instructorActor(), command(INSTRUCTOR_ID));

        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.instructorId()).isEqualTo(INSTRUCTOR_ID);
        assertThat(result.courtId()).isEqualTo(COURT_ID);
        assertThat(result.isActive()).isTrue();
        verify(classSlotRepository).save(any(ClassSlot.class));
        // F17: validateSchedule debe correr antes que validateRecurrence y antes de save — ver
        // docs/loop/F17.md, IMPLEMENT.
        InOrder order = inOrder(validateSchedule, validateRecurrence, classSlotRepository);
        order.verify(validateSchedule).execute(any(ClassSlot.class));
        order.verify(validateRecurrence).execute(any(ClassSlot.class));
        order.verify(classSlotRepository).save(any(ClassSlot.class));
    }

    @Test
    void execute_adminCreaParaOtroInstructor_sePermite() {
        stubHappyPathDependencies(INSTRUCTOR_ID);

        ClassSlot result = useCase.execute(adminActor(), command(INSTRUCTOR_ID));

        assertThat(result.instructorId()).isEqualTo(INSTRUCTOR_ID);
        verify(classSlotRepository).save(any(ClassSlot.class));
    }

    // ─── Ownership ──────────────────────────────────────────────────────────────

    @Test
    void execute_instructorIntentaCrearParaOtroInstructor_lanzaInstructorNotFoundSinEscrituras() {
        Actor otherInstructor = new Actor(777L, ActorRole.INSTRUCTOR);

        assertThatThrownBy(() -> useCase.execute(otherInstructor, command(INSTRUCTOR_ID)))
                .isInstanceOf(InstructorNotFoundException.class);

        verifyNoInteractions(instructorGateway, courtRepository, branchRepository, classSlotRepository);
    }

    // ─── Recursos faltantes ─────────────────────────────────────────────────────

    @Test
    void execute_instructorInexistente_lanzaInstructorNotFound() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(instructorGateway.findById(INSTRUCTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(instructorActor(), command(INSTRUCTOR_ID)))
                .isInstanceOf(InstructorNotFoundException.class);

        verifyNoInteractions(branchRepository, classSlotRepository);
    }

    @Test
    void execute_canchaInexistente_lanzaCourtNotFound() {
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(instructorActor(), command(INSTRUCTOR_ID)))
                .isInstanceOf(CourtNotFoundException.class);

        verify(branchRepository, never()).findById(any());
        verify(classSlotRepository, never()).save(any());
    }

    @Test
    void execute_canchaInactiva_lanzaCourtNotActive() {
        when(instructorGateway.findById(INSTRUCTOR_ID)).thenReturn(Optional.of(new InstructorSnapshot(INSTRUCTOR_ID)));
        Court inactive = new Court(COURT_ID, "Cancha 1", 1000.0, BRANCH_ID, 1L, "Pádel", ActiveStatus.INACTIVE);
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> useCase.execute(instructorActor(), command(INSTRUCTOR_ID)))
                .isInstanceOf(CourtNotActiveException.class);

        verify(branchRepository, never()).findById(any());
        verify(classSlotRepository, never()).save(any());
    }

    @Test
    void execute_sucursalInexistente_lanzaBranchNotFound() {
        when(instructorGateway.findById(INSTRUCTOR_ID)).thenReturn(Optional.of(new InstructorSnapshot(INSTRUCTOR_ID)));
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(instructorActor(), command(INSTRUCTOR_ID)))
                .isInstanceOf(BranchNotFoundException.class);

        verify(classSlotRepository, never()).save(any());
    }

    @Test
    void execute_sucursalNoAprobada_lanzaBranchNotApproved() {
        when(instructorGateway.findById(INSTRUCTOR_ID)).thenReturn(Optional.of(new InstructorSnapshot(INSTRUCTOR_ID)));
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        Address address = new Address("Calle Falsa", 123, "CABA", "Buenos Aires", 1000, -34.6, -58.4);
        Branch pending = new Branch(BRANCH_ID, "Sucursal 1", address, 10L, VerificationStatus.PENDING, ActiveStatus.INACTIVE, 12);
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase.execute(instructorActor(), command(INSTRUCTOR_ID)))
                .isInstanceOf(BranchNotApprovedException.class);

        verify(classSlotRepository, never()).save(any());
    }

    // ─── Delegación a reglas de dominio (no se duplican los tests de 1A) ─────────

    @Test
    void execute_capacidadInvalida_propagaInvalidCapacityExceptionSinGuardar() {
        when(instructorGateway.findById(INSTRUCTOR_ID)).thenReturn(Optional.of(new InstructorSnapshot(INSTRUCTOR_ID)));
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        CreateClassSlotCommand invalidCommand = new CreateClassSlotCommand(INSTRUCTOR_ID, COURT_ID,
                DayOfWeek.THURSDAY, LocalTime.of(15, 0), Duration.ofHours(1), Level.INTERMEDIO, 0);

        assertThatThrownBy(() -> useCase.execute(instructorActor(), invalidCommand))
                .isInstanceOf(InvalidCapacityException.class);

        verify(classSlotRepository, never()).save(any());
    }

    // ─── F17 — alineación con la grilla de Schedule (docs/loop/F17.md) ───────────
    // La lógica de qué cuenta como "alineado" (isValidSlot + duration exacta) la prueba
    // ValidateClassSlotScheduleTest de forma aislada; acá solo se verifica que este caso de uso
    // invoca a ese validador en el orden correcto y propaga lo que sea que lance, sin guardar.

    @Test
    void execute_agendaInexistente_propagaScheduleNotFoundSinGuardar() {
        when(instructorGateway.findById(INSTRUCTOR_ID)).thenReturn(Optional.of(new InstructorSnapshot(INSTRUCTOR_ID)));
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        doThrow(new ScheduleNotFoundException("No hay agenda disponible para ese día en esta cancha"))
                .when(validateSchedule).execute(any(ClassSlot.class));

        assertThatThrownBy(() -> useCase.execute(instructorActor(), command(INSTRUCTOR_ID)))
                .isInstanceOf(ScheduleNotFoundException.class);

        verify(classSlotRepository, never()).save(any());
        verifyNoInteractions(validateRecurrence);
    }

    @Test
    void execute_horarioNoCoincideConLaGrilla_propagaClassSlotScheduleMismatchSinGuardar() {
        when(instructorGateway.findById(INSTRUCTOR_ID)).thenReturn(Optional.of(new InstructorSnapshot(INSTRUCTOR_ID)));
        when(courtRepository.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        doThrow(new ClassSlotScheduleMismatchException("no coincide con la grilla"))
                .when(validateSchedule).execute(any(ClassSlot.class));

        assertThatThrownBy(() -> useCase.execute(instructorActor(), command(INSTRUCTOR_ID)))
                .isInstanceOf(ClassSlotScheduleMismatchException.class);

        verify(classSlotRepository, never()).save(any());
        verifyNoInteractions(validateRecurrence);
    }
}

