package com.deportlink.deportlink.usecase.classsession;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.CourtGateway.CourtSnapshot;
import com.deportlink.deportlink.application.usecase.classsession.ClassSessionSummary;
import com.deportlink.deportlink.application.usecase.classsession.GetClassSessionsByInstructorUseCase;
import com.deportlink.deportlink.domain.model.ClassSession;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort.AttendanceCounts;
import com.deportlink.deportlink.domain.port.out.ClassSessionRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.ClassSessionStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.InstructorNotFoundException;
import com.deportlink.deportlink.exception.InvalidTimeRangeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetClassSessionsByInstructorUseCaseTest {

    @Mock private ClassSessionRepositoryPort classSessionRepository;
    @Mock private ClassSlotRepositoryPort classSlotRepository;
    @Mock private ClassAttendanceRepositoryPort classAttendanceRepository;
    @Mock private CourtGateway courtGateway;

    private static final Long INSTRUCTOR_ID = 10L;
    private static final Long COURT_ID = 2L;

    // Clock fijo (no un mock): "hoy" es determinístico para el default de esta etapa.
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private GetClassSessionsByInstructorUseCase useCase() {
        return new GetClassSessionsByInstructorUseCase(
                classSessionRepository, classSlotRepository, classAttendanceRepository, courtGateway, FIXED_CLOCK);
    }

    private static Actor ownerInstructor() { return new Actor(INSTRUCTOR_ID, ActorRole.INSTRUCTOR); }
    private static Actor otherInstructor() { return new Actor(999L, ActorRole.INSTRUCTOR); }

    private static ClassSlot slot(Long id) {
        return new ClassSlot(id, INSTRUCTOR_ID, COURT_ID, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4, ActiveStatus.ACTIVE);
    }

    private static ClassSession session(Long id, Long slotId, LocalDate day, LocalTime time) {
        return new ClassSession(id, slotId, day, time, Duration.ofHours(1), ClassSessionStatus.SCHEDULED);
    }

    private static CourtSnapshot court() {
        return new CourtSnapshot(COURT_ID, 1000.0, "Cancha 2", "Pádel", "Sucursal 1", "Dirección 1", 12);
    }

    // ─── Ownership ──────────────────────────────────────────────────────────────

    @Test
    void execute_instructorAjeno_lanzaInstructorNotFoundSinConsultarNada() {
        assertThatThrownBy(() -> useCase().execute(otherInstructor(), INSTRUCTOR_ID, null, null))
                .isInstanceOf(InstructorNotFoundException.class);

        verifyNoInteractions(classSessionRepository, classSlotRepository, classAttendanceRepository, courtGateway);
    }

    // ─── Rango de fechas ────────────────────────────────────────────────────────

    @Test
    void execute_sinFechas_usaHoyComoDesdeYSinLimiteSuperior() {
        when(classSessionRepository.findByInstructor(eq(INSTRUCTOR_ID), eq(TODAY), eq(null)))
                .thenReturn(List.of());

        List<ClassSessionSummary> result = useCase().execute(ownerInstructor(), INSTRUCTOR_ID, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void execute_rangoInvertido_lanzaInvalidTimeRangeSinConsultarSesiones() {
        LocalDate from = TODAY.plusDays(10);
        LocalDate to = TODAY.plusDays(5);

        assertThatThrownBy(() -> useCase().execute(ownerInstructor(), INSTRUCTOR_ID, from, to))
                .isInstanceOf(InvalidTimeRangeException.class);

        verifyNoInteractions(classSessionRepository);
    }

    @Test
    void execute_agendaVacia_devuelveListaVaciaSinConsultarSlotsNiAsistencias() {
        LocalDate from = TODAY;
        when(classSessionRepository.findByInstructor(INSTRUCTOR_ID, from, null)).thenReturn(List.of());

        List<ClassSessionSummary> result = useCase().execute(ownerInstructor(), INSTRUCTOR_ID, from, null);

        assertThat(result).isEmpty();
        verifyNoInteractions(classSlotRepository, classAttendanceRepository, courtGateway);
    }

    // ─── Cálculo de ocupación y orden ───────────────────────────────────────────

    @Test
    void execute_estadosMezclados_calculaOcupacionYDisponiblesCorrectamente() {
        LocalDate day = TODAY.plusDays(3);
        ClassSession s = session(77L, 1L, day, LocalTime.of(15, 0));
        when(classSessionRepository.findByInstructor(INSTRUCTOR_ID, TODAY, null)).thenReturn(List.of(s));
        when(classSlotRepository.findAllByIds(Set.of(1L))).thenReturn(List.of(slot(1L)));
        when(classAttendanceRepository.countByStatusForSessions(Set.of(77L)))
                .thenReturn(Map.of(77L, new AttendanceCounts(1, 2, 1))); // 1 PENDING, 2 CONFIRMED, 1 CANCELLED
        when(courtGateway.findById(COURT_ID)).thenReturn(java.util.Optional.of(court()));

        List<ClassSessionSummary> result = useCase().execute(ownerInstructor(), INSTRUCTOR_ID, null, null);

        assertThat(result).hasSize(1);
        ClassSessionSummary summary = result.get(0);
        assertThat(summary.occupancy()).isEqualTo(3); // 1 pending + 2 confirmed, NO cuenta cancelled
        assertThat(summary.availableSpots()).isEqualTo(1); // capacidad 4 - ocupación 3
        assertThat(summary.courtName()).isEqualTo("Cancha 2");
        assertThat(summary.level()).isEqualTo(Level.INTERMEDIO);
    }

    @Test
    void execute_variasSesiones_seOrdenanPorFechaHoraEId() {
        LocalDate day1 = TODAY.plusDays(1);
        LocalDate day2 = TODAY.plusDays(2);
        ClassSession later = session(3L, 1L, day2, LocalTime.of(10, 0));
        ClassSession earlierSameDayLaterTime = session(2L, 1L, day1, LocalTime.of(18, 0));
        ClassSession earliest = session(1L, 1L, day1, LocalTime.of(9, 0));

        when(classSessionRepository.findByInstructor(INSTRUCTOR_ID, TODAY, null))
                .thenReturn(List.of(later, earlierSameDayLaterTime, earliest));
        when(classSlotRepository.findAllByIds(Set.of(1L))).thenReturn(List.of(slot(1L)));
        when(classAttendanceRepository.countByStatusForSessions(any())).thenReturn(Map.of());
        when(courtGateway.findById(COURT_ID)).thenReturn(java.util.Optional.of(court()));

        List<ClassSessionSummary> result = useCase().execute(ownerInstructor(), INSTRUCTOR_ID, null, null);

        assertThat(result).extracting(ClassSessionSummary::sessionId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void execute_mismaCanchaEnVariasSesiones_consultaCourtGatewayUnaSolaVez() {
        LocalDate day = TODAY.plusDays(1);
        ClassSession s1 = session(1L, 1L, day, LocalTime.of(9, 0));
        ClassSession s2 = session(2L, 1L, day, LocalTime.of(18, 0));

        when(classSessionRepository.findByInstructor(INSTRUCTOR_ID, TODAY, null)).thenReturn(List.of(s1, s2));
        when(classSlotRepository.findAllByIds(Set.of(1L))).thenReturn(List.of(slot(1L)));
        when(classAttendanceRepository.countByStatusForSessions(any())).thenReturn(Map.of());
        when(courtGateway.findById(COURT_ID)).thenReturn(java.util.Optional.of(court()));

        useCase().execute(ownerInstructor(), INSTRUCTOR_ID, null, null);

        org.mockito.Mockito.verify(courtGateway, org.mockito.Mockito.times(1)).findById(COURT_ID);
    }
}
