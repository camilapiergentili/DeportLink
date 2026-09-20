package com.deportlink.deportlink.usecase.classsession;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.CourtGateway.CourtSnapshot;
import com.deportlink.deportlink.application.port.out.PlayerGateway.PlayerSnapshot;
import com.deportlink.deportlink.application.port.out.PlayerRosterGateway;
import com.deportlink.deportlink.application.usecase.classsession.ClassSessionDetail;
import com.deportlink.deportlink.application.usecase.classsession.GetClassSessionDetailUseCase;
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
import com.deportlink.deportlink.exception.ClassSessionNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetClassSessionDetailUseCaseTest {

    @Mock private ClassSessionRepositoryPort classSessionRepository;
    @Mock private ClassSlotRepositoryPort classSlotRepository;
    @Mock private ClassAttendanceRepositoryPort classAttendanceRepository;
    @Mock private CourtGateway courtGateway;
    @Mock private PlayerRosterGateway playerRosterGateway;

    @InjectMocks private GetClassSessionDetailUseCase useCase;

    private static final Long SESSION_ID = 77L;
    private static final Long SLOT_ID = 1L;
    private static final Long COURT_ID = 2L;
    private static final Long INSTRUCTOR_ID = 10L;

    private static Actor ownerInstructor() { return new Actor(INSTRUCTOR_ID, ActorRole.INSTRUCTOR); }
    private static Actor otherInstructor() { return new Actor(999L, ActorRole.INSTRUCTOR); }

    private static ClassSession session() {
        return new ClassSession(SESSION_ID, SLOT_ID, LocalDate.now().plusDays(2), LocalTime.of(15, 0),
                Duration.ofHours(1), ClassSessionStatus.SCHEDULED);
    }

    private static ClassSlot slot() {
        return new ClassSlot(SLOT_ID, INSTRUCTOR_ID, COURT_ID, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4, ActiveStatus.ACTIVE);
    }

    private static CourtSnapshot court() {
        return new CourtSnapshot(COURT_ID, 1000.0, "Cancha 2", "Pádel", "Sucursal 1", "Dirección 1", 12);
    }

    @Test
    void execute_devuelveResumenYRosterConNombresResueltos() {
        ClassAttendance a1 = ClassAttendance.createPending(SESSION_ID, 100L).confirm().withId(1L);
        ClassAttendance a2 = ClassAttendance.createPending(SESSION_ID, 200L).withId(2L);

        when(classSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(classSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot()));
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(classAttendanceRepository.findByClassSessionId(SESSION_ID)).thenReturn(List.of(a1, a2));
        when(playerRosterGateway.findByIds(Set.of(100L, 200L))).thenReturn(Map.of(
                100L, new PlayerSnapshot(100L, "Juan", "Pérez"),
                200L, new PlayerSnapshot(200L, "María", "Gómez")
        ));

        ClassSessionDetail detail = useCase.execute(ownerInstructor(), SESSION_ID);

        assertThat(detail.summary().courtName()).isEqualTo("Cancha 2");
        assertThat(detail.summary().level()).isEqualTo(Level.INTERMEDIO);
        assertThat(detail.summary().capacity()).isEqualTo(4);
        assertThat(detail.attendees()).hasSize(2);
        assertThat(detail.attendees()).extracting("playerName").containsExactlyInAnyOrder("Juan Pérez", "María Gómez");
        assertThat(detail.attendees()).extracting("status")
                .containsExactlyInAnyOrder(ClassAttendanceStatus.CONFIRMED, ClassAttendanceStatus.PENDING);
    }

    @Test
    void execute_conservaAsistenciaAunqueElEnrollmentEsteInactivo() {
        // El detalle no consulta ClassEnrollment en ningún momento — el roster sale
        // enteramente de ClassAttendance, que es historial independiente.
        ClassAttendance attendance = ClassAttendance.createPending(SESSION_ID, 100L).withId(1L);

        when(classSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(classSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot()));
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(classAttendanceRepository.findByClassSessionId(SESSION_ID)).thenReturn(List.of(attendance));
        when(playerRosterGateway.findByIds(Set.of(100L))).thenReturn(Map.of(100L, new PlayerSnapshot(100L, "Juan", "Pérez")));

        ClassSessionDetail detail = useCase.execute(ownerInstructor(), SESSION_ID);

        assertThat(detail.attendees()).hasSize(1);
        assertThat(detail.attendees().get(0).playerId()).isEqualTo(100L);
    }

    @Test
    void execute_sesionInexistente_lanzaClassSessionNotFound() {
        when(classSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SESSION_ID))
                .isInstanceOf(ClassSessionNotFoundException.class);

        verifyNoInteractions(classSlotRepository, courtGateway, classAttendanceRepository, playerRosterGateway);
    }

    @Test
    void execute_instructorAjeno_lanzaClassSessionNotFoundSinExponerAlumnos() {
        when(classSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(classSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot()));

        assertThatThrownBy(() -> useCase.execute(otherInstructor(), SESSION_ID))
                .isInstanceOf(ClassSessionNotFoundException.class);

        verifyNoInteractions(courtGateway, classAttendanceRepository, playerRosterGateway);
    }
}
