package com.deportlink.deportlink.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.usecase.classslot.GetClassSlotsByInstructorUseCase;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.InstructorNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetClassSlotsByInstructorUseCaseTest {

    @Mock private ClassSlotRepositoryPort classSlotRepository;
    @InjectMocks private GetClassSlotsByInstructorUseCase useCase;

    private static final Long INSTRUCTOR_ID = 10L;

    private static ClassSlot slot(Long id, ActiveStatus status) {
        return new ClassSlot(id, INSTRUCTOR_ID, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4, status);
    }

    private static Actor ownerInstructor() { return new Actor(INSTRUCTOR_ID, ActorRole.INSTRUCTOR); }
    private static Actor otherInstructor() { return new Actor(999L, ActorRole.INSTRUCTOR); }
    private static Actor admin() { return new Actor(999L, ActorRole.ADMIN); }

    @Test
    void execute_devuelveActivosYPausadosPorIgual() {
        when(classSlotRepository.findAllByInstructorId(INSTRUCTOR_ID))
                .thenReturn(List.of(slot(1L, ActiveStatus.ACTIVE), slot(2L, ActiveStatus.INACTIVE)));

        List<ClassSlot> result = useCase.execute(ownerInstructor(), INSTRUCTOR_ID);

        assertThat(result).hasSize(2);
    }

    @Test
    void execute_sinHorarios_devuelveListaVacia() {
        when(classSlotRepository.findAllByInstructorId(INSTRUCTOR_ID)).thenReturn(List.of());

        List<ClassSlot> result = useCase.execute(ownerInstructor(), INSTRUCTOR_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void execute_adminConsultaCualquierInstructor_sePermite() {
        when(classSlotRepository.findAllByInstructorId(INSTRUCTOR_ID)).thenReturn(List.of(slot(1L, ActiveStatus.ACTIVE)));

        List<ClassSlot> result = useCase.execute(admin(), INSTRUCTOR_ID);

        assertThat(result).hasSize(1);
    }

    @Test
    void execute_instructorAjeno_lanzaInstructorNotFoundSinConsultarElRepositorio() {
        assertThatThrownBy(() -> useCase.execute(otherInstructor(), INSTRUCTOR_ID))
                .isInstanceOf(InstructorNotFoundException.class);

        verifyNoInteractions(classSlotRepository);
    }
}
