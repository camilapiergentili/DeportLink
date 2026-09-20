package com.deportlink.deportlink.usecase.classsession;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.port.out.ClassSlotCourtGateway;
import com.deportlink.deportlink.application.port.out.CourtOccupancyPort;
import com.deportlink.deportlink.application.usecase.classsession.CreateClassSessionUseCase;
import com.deportlink.deportlink.domain.model.Address;
import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.domain.model.ClassAttendance;
import com.deportlink.deportlink.domain.model.ClassEnrollment;
import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.model.Court;
import com.deportlink.deportlink.domain.port.out.BranchRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassEnrollmentRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSessionRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.domain.port.out.CourtRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.ClassSessionAlreadyExistsException;
import com.deportlink.deportlink.exception.ClassSessionDayMismatchException;
import com.deportlink.deportlink.exception.ClassSlotNotActiveException;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import com.deportlink.deportlink.exception.CourtNotActiveException;
import com.deportlink.deportlink.exception.CourtSlotOccupiedException;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateClassSessionUseCaseTest {

    @Mock private ClassSlotCourtGateway classSlotCourtGateway;
    @Mock private ClassSlotRepositoryPort classSlotRepository;
    @Mock private CourtRepositoryPort courtRepository;
    @Mock private BranchRepositoryPort branchRepository;
    @Mock private ClassSessionRepositoryPort classSessionRepository;
    @Mock private CourtOccupancyPort courtOccupancyPort;
    @Mock private ClassEnrollmentRepositoryPort classEnrollmentRepository;
    @Mock private ClassAttendanceRepositoryPort classAttendanceRepository;

    @InjectMocks private CreateClassSessionUseCase useCase;

    private static final Long SLOT_ID = 1L;
    private static final Long COURT_ID = 2L;
    private static final Long BRANCH_ID = 3L;
    private static final Long INSTRUCTOR_ID = 10L;
    private static final LocalDate NEXT_THURSDAY = nextThursday();
    private static final LocalTime START_TIME = LocalTime.of(15, 0);

    private static LocalDate nextThursday() {
        LocalDate day = LocalDate.now().plusDays(1);
        while (day.getDayOfWeek() != DayOfWeek.THURSDAY) day = day.plusDays(1);
        return day;
    }

    private static Actor ownerInstructor() { return new Actor(INSTRUCTOR_ID, ActorRole.INSTRUCTOR); }
    private static Actor otherInstructor() { return new Actor(999L, ActorRole.INSTRUCTOR); }

    private static ClassSlot activeSlot() {
        return new ClassSlot(SLOT_ID, INSTRUCTOR_ID, COURT_ID, DayOfWeek.THURSDAY, START_TIME,
                Duration.ofHours(1), Level.INTERMEDIO, 4, ActiveStatus.ACTIVE);
    }

    private static Court activeCourt() {
        return new Court(COURT_ID, "Cancha 1", 1000.0, BRANCH_ID, 1L, "Pádel", ActiveStatus.ACTIVE);
    }

    private static Branch approvedBranch() {
        Address address = new Address("Calle Falsa", 123, "CABA", "Buenos Aires", 1000, -34.6, -58.4);
        return new Branch(BRANCH_ID, "Sucursal 1", address, 10L, VerificationStatus.APPROVED, ActiveStatus.ACTIVE, 12);
    }

    /** Deja el flujo listo hasta (e incluyendo) la verificación de ocupación, sin alumnos por defecto. */
    private void stubUpToOccupancyCheck() {
        when(classSlotCourtGateway.findCourtIdByClassSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(COURT_ID));
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot()));
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        when(classSessionRepository.existsByClassSlotIdAndDay(SLOT_ID, NEXT_THURSDAY)).thenReturn(false);
        when(courtOccupancyPort.existsOccupancy(COURT_ID, NEXT_THURSDAY, START_TIME)).thenReturn(false);
        when(classEnrollmentRepository.findActiveByClassSlotId(SLOT_ID)).thenReturn(List.of());
    }

    // ─── Camino exitoso ─────────────────────────────────────────────────────────

    @Test
    void execute_sinAlumnos_creaLaSesionSinAsistenciasYRegistraOcupacion() {
        stubUpToOccupancyCheck();
        when(classSessionRepository.save(any(ClassSession.class)))
                .thenAnswer(inv -> inv.getArgument(0, ClassSession.class).withId(77L));

        ClassSession result = useCase.execute(ownerInstructor(), SLOT_ID, NEXT_THURSDAY);

        assertThat(result.id()).isEqualTo(77L);
        verify(courtOccupancyPort).registerForClassSession(77L, COURT_ID, NEXT_THURSDAY, START_TIME);
        verify(classAttendanceRepository, never()).saveAll(anyList());
    }

    @Test
    void execute_conAlumnosActivos_generaUnaAsistenciaPendingPorCadaUnoUsandoElIdDeLaSesionGuardada() {
        stubUpToOccupancyCheck();
        ClassEnrollment e1 = new ClassEnrollment(1L, SLOT_ID, 100L, true);
        ClassEnrollment e2 = new ClassEnrollment(2L, SLOT_ID, 200L, true);
        when(classEnrollmentRepository.findActiveByClassSlotId(SLOT_ID)).thenReturn(List.of(e1, e2));
        when(classSessionRepository.save(any(ClassSession.class)))
                .thenAnswer(inv -> inv.getArgument(0, ClassSession.class).withId(77L));

        useCase.execute(ownerInstructor(), SLOT_ID, NEXT_THURSDAY);

        ArgumentCaptor<List<ClassAttendance>> captor = ArgumentCaptor.forClass(List.class);
        verify(classAttendanceRepository).saveAll(captor.capture());
        List<ClassAttendance> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(a -> a.classSessionId().equals(77L));
        assertThat(saved).extracting(ClassAttendance::playerId).containsExactlyInAnyOrder(100L, 200L);
        assertThat(saved).allMatch(a -> a.status() == com.deportlink.deportlink.enums.ClassAttendanceStatus.PENDING);
    }

    @Test
    void execute_ordenDeLocks_bloqueaCourtAntesQueClassSlotYAntesQueOcupacion() {
        stubUpToOccupancyCheck();
        when(classSessionRepository.save(any(ClassSession.class)))
                .thenAnswer(inv -> inv.getArgument(0, ClassSession.class).withId(77L));

        useCase.execute(ownerInstructor(), SLOT_ID, NEXT_THURSDAY);

        InOrder order = inOrder(classSlotCourtGateway, classSlotRepository, courtOccupancyPort, classSessionRepository);
        order.verify(classSlotCourtGateway).findCourtIdByClassSlotForUpdate(SLOT_ID);
        order.verify(classSlotRepository).findByIdForUpdate(SLOT_ID);
        order.verify(classSessionRepository).save(any(ClassSession.class));
        order.verify(courtOccupancyPort).registerForClassSession(any(), any(), any(), any());
    }

    // ─── Recurso no encontrado / ownership ──────────────────────────────────────

    @Test
    void execute_classSlotInexistente_lanzaClassSlotNotFoundSinMasLecturas() {
        when(classSlotCourtGateway.findCourtIdByClassSlotForUpdate(SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, NEXT_THURSDAY))
                .isInstanceOf(ClassSlotNotFoundException.class);

        verifyNoInteractions(classSlotRepository, courtRepository, branchRepository,
                classSessionRepository, courtOccupancyPort, classEnrollmentRepository, classAttendanceRepository);
    }

    @Test
    void execute_instructorAjeno_lanzaClassSlotNotFound() {
        when(classSlotCourtGateway.findCourtIdByClassSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(COURT_ID));
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot()));

        assertThatThrownBy(() -> useCase.execute(otherInstructor(), SLOT_ID, NEXT_THURSDAY))
                .isInstanceOf(ClassSlotNotFoundException.class);

        verifyNoInteractions(courtRepository, branchRepository, classSessionRepository, courtOccupancyPort);
    }

    // ─── Estados requeridos ─────────────────────────────────────────────────────

    @Test
    void execute_classSlotPausado_lanzaClassSlotNotActive() {
        when(classSlotCourtGateway.findCourtIdByClassSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(COURT_ID));
        ClassSlot paused = activeSlot().pause();
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(paused));

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, NEXT_THURSDAY))
                .isInstanceOf(ClassSlotNotActiveException.class);

        verifyNoInteractions(courtRepository, branchRepository, classSessionRepository);
    }

    @Test
    void execute_canchaInactiva_lanzaCourtNotActive() {
        when(classSlotCourtGateway.findCourtIdByClassSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(COURT_ID));
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot()));
        Court inactive = new Court(COURT_ID, "Cancha 1", 1000.0, BRANCH_ID, 1L, "Pádel", ActiveStatus.INACTIVE);
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, NEXT_THURSDAY))
                .isInstanceOf(CourtNotActiveException.class);

        verifyNoInteractions(branchRepository, classSessionRepository);
    }

    @Test
    void execute_sucursalNoAprobada_lanzaBranchNotApproved() {
        when(classSlotCourtGateway.findCourtIdByClassSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(COURT_ID));
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot()));
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        Address address = new Address("Calle Falsa", 123, "CABA", "Buenos Aires", 1000, -34.6, -58.4);
        Branch pending = new Branch(BRANCH_ID, "Sucursal 1", address, 10L, VerificationStatus.PENDING, ActiveStatus.INACTIVE, 12);
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, NEXT_THURSDAY))
                .isInstanceOf(BranchNotApprovedException.class);

        verifyNoInteractions(classSessionRepository);
    }

    // ─── Fecha / día de semana ──────────────────────────────────────────────────

    @Test
    void execute_diaDeSemanaNoCoincide_lanzaClassSessionDayMismatch() {
        when(classSlotCourtGateway.findCourtIdByClassSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(COURT_ID));
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot()));
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));

        LocalDate aFridayNotThursday = NEXT_THURSDAY.plusDays(1);

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, aFridayNotThursday))
                .isInstanceOf(ClassSessionDayMismatchException.class);

        verifyNoInteractions(classSessionRepository, courtOccupancyPort);
    }

    @Test
    void execute_fechaPasada_propagaInvalidTimeRangeDelDominio() {
        when(classSlotCourtGateway.findCourtIdByClassSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(COURT_ID));
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot()));
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));

        // minusWeeks(1) podía caer en "hoy" (si NEXT_THURSDAY está a exactamente 7 días) y
        // dejar el resultado del test a merced de la hora de ejecución (antes o después de las
        // 15:00). minusWeeks(2) garantiza una fecha inequívocamente pasada sin importar la hora
        // en que corra el test, conservando el jueves.
        LocalDate pastThursday = NEXT_THURSDAY.minusWeeks(2);

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, pastThursday))
                .isInstanceOf(InvalidTimeRangeException.class);

        verifyNoInteractions(classSessionRepository, courtOccupancyPort);
    }

    // ─── Duplicados y ocupación ─────────────────────────────────────────────────

    @Test
    void execute_sesionYaExisteParaEsaFecha_lanzaClassSessionAlreadyExists() {
        when(classSlotCourtGateway.findCourtIdByClassSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(COURT_ID));
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot()));
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        when(classSessionRepository.existsByClassSlotIdAndDay(SLOT_ID, NEXT_THURSDAY)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, NEXT_THURSDAY))
                .isInstanceOf(ClassSessionAlreadyExistsException.class);

        verifyNoInteractions(courtOccupancyPort);
        verify(classSessionRepository, never()).save(any());
    }

    @Test
    void execute_ocupacionExistentePorReserva_lanzaCourtSlotOccupied() {
        // No se usa stubUpToOccupancyCheck(): esa fecha nunca llega a leer enrollments activos
        // (la excepción corta el flujo antes), y dejar ese stub sin consumir es exactamente lo
        // que Mockito strict-stubs marca como unnecessary stubbing.
        when(classSlotCourtGateway.findCourtIdByClassSlotForUpdate(SLOT_ID)).thenReturn(Optional.of(COURT_ID));
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(activeSlot()));
        when(courtRepository.findById(COURT_ID)).thenReturn(Optional.of(activeCourt()));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(approvedBranch()));
        when(classSessionRepository.existsByClassSlotIdAndDay(SLOT_ID, NEXT_THURSDAY)).thenReturn(false);
        when(courtOccupancyPort.existsOccupancy(COURT_ID, NEXT_THURSDAY, START_TIME)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, NEXT_THURSDAY))
                .isInstanceOf(CourtSlotOccupiedException.class);

        // El origen (Reservation u otra ClassSession) es indistinguible para este caso de uso:
        // el puerto solo informa "ocupado o no", sin importar quién lo ocupó.
        verifyNoInteractions(classEnrollmentRepository);
        verify(classSessionRepository, never()).save(any());
        verify(classAttendanceRepository, never()).saveAll(anyList());
    }

    // ─── Ninguna captura de errores de persistencia ─────────────────────────────

    @Test
    void execute_fallaAlGuardarLaSesion_propagaLaExcepcionSinRegistrarOcupacionNiAsistencias() {
        stubUpToOccupancyCheck();
        when(classSessionRepository.save(any(ClassSession.class))).thenThrow(new RuntimeException("fallo de persistencia simulado"));

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, NEXT_THURSDAY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("fallo de persistencia simulado");

        // existsOccupancy() ya se había consultado antes del save() que falla (paso 7 antes que
        // el 9) — lo que NO debe pasar es registrar la ocupación ni las asistencias, porque la
        // sesión nunca terminó de persistirse.
        verify(courtOccupancyPort, never()).registerForClassSession(any(), any(), any(), any());
        verify(classAttendanceRepository, never()).saveAll(anyList());
    }
}
