package com.deportlink.deportlink.usecase.classslot;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.actor.ActorRole;
import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.application.port.out.PlayerGateway.PlayerSnapshot;
import com.deportlink.deportlink.application.usecase.classslot.AddPlayerToClassSlotUseCase;
import com.deportlink.deportlink.domain.model.ClassEnrollment;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.ClassEnrollmentRepositoryPort;
import com.deportlink.deportlink.domain.port.out.ClassSlotRepositoryPort;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.Level;
import com.deportlink.deportlink.exception.ClassSlotFullException;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import com.deportlink.deportlink.exception.PlayerAlreadyEnrolledException;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddPlayerToClassSlotUseCaseTest {
    @Mock private com.deportlink.deportlink.application.usecase.classattendance.SyncFutureClassRoster syncFutureRoster;

    @Mock private ClassSlotRepositoryPort classSlotRepository;
    @Mock private ClassEnrollmentRepositoryPort classEnrollmentRepository;
    @Mock private PlayerGateway playerGateway;

    @InjectMocks private AddPlayerToClassSlotUseCase useCase;

    private static final Long SLOT_ID = 1L;
    private static final Long INSTRUCTOR_ID = 10L;
    private static final Long PLAYER_ID = 20L;

    private static ClassSlot slotWithCapacity(int capacity) {
        return new ClassSlot(SLOT_ID, INSTRUCTOR_ID, 2L, DayOfWeek.THURSDAY, LocalTime.of(15, 0),
                Duration.ofHours(1), Level.INTERMEDIO, capacity, ActiveStatus.ACTIVE);
    }

    private static Actor ownerInstructor() { return new Actor(INSTRUCTOR_ID, ActorRole.INSTRUCTOR); }
    private static Actor otherInstructor() { return new Actor(999L, ActorRole.INSTRUCTOR); }

    private static PlayerSnapshot player() { return new PlayerSnapshot(PLAYER_ID, "Juan", "Pérez"); }

    // ─── Camino exitoso ─────────────────────────────────────────────────────────

    @Test
    void execute_sinEnrollmentPrevio_creaUnoNuevo() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slotWithCapacity(4)));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(classEnrollmentRepository.findByClassSlotIdAndPlayerId(SLOT_ID, PLAYER_ID)).thenReturn(Optional.empty());
        when(classEnrollmentRepository.countActiveByClassSlotId(SLOT_ID)).thenReturn(2);
        when(classEnrollmentRepository.save(any(ClassEnrollment.class))).thenAnswer(inv -> inv.getArgument(0, ClassEnrollment.class).withId(99L));

        ClassEnrollment result = useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID);

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.active()).isTrue();
        assertThat(result.playerId()).isEqualTo(PLAYER_ID);
    }

    @Test
    void execute_ultimoLugarDisponible_sePermiteYGuarda() {
        // capacidad 4, 3 activos -> hasRoom(3) es true, este es el 4to.
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slotWithCapacity(4)));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(classEnrollmentRepository.findByClassSlotIdAndPlayerId(SLOT_ID, PLAYER_ID)).thenReturn(Optional.empty());
        when(classEnrollmentRepository.countActiveByClassSlotId(SLOT_ID)).thenReturn(3);
        when(classEnrollmentRepository.save(any(ClassEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassEnrollment result = useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID);

        assertThat(result.active()).isTrue();
        verify(classEnrollmentRepository).save(any(ClassEnrollment.class));
    }

    @Test
    void execute_enrollmentInactivoPrevio_loReactivaConservandoIdEnVezDeCrearUnoNuevo() {
        ClassEnrollment inactive = new ClassEnrollment(55L, SLOT_ID, PLAYER_ID, false);
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slotWithCapacity(4)));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(classEnrollmentRepository.findByClassSlotIdAndPlayerId(SLOT_ID, PLAYER_ID)).thenReturn(Optional.of(inactive));
        when(classEnrollmentRepository.countActiveByClassSlotId(SLOT_ID)).thenReturn(1);
        when(classEnrollmentRepository.save(any(ClassEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        ClassEnrollment result = useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID);

        assertThat(result.id()).isEqualTo(55L);
        assertThat(result.active()).isTrue();
    }

    // ─── Ownership ──────────────────────────────────────────────────────────────

    @Test
    void execute_instructorAjeno_lanzaClassSlotNotFoundSinTocarPlayerNiEnrollment() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slotWithCapacity(4)));

        assertThatThrownBy(() -> useCase.execute(otherInstructor(), SLOT_ID, PLAYER_ID))
                .isInstanceOf(ClassSlotNotFoundException.class);

        verifyNoInteractions(playerGateway, classEnrollmentRepository);
    }

    // ─── Recursos faltantes ─────────────────────────────────────────────────────

    @Test
    void execute_slotInexistente_lanzaClassSlotNotFound() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID))
                .isInstanceOf(ClassSlotNotFoundException.class);

        verifyNoInteractions(playerGateway, classEnrollmentRepository);
    }

    @Test
    void execute_playerInexistente_lanzaPlayerNotFound() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slotWithCapacity(4)));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID))
                .isInstanceOf(PlayerNotFoundException.class);

        verify(classEnrollmentRepository, never()).save(any());
    }

    // ─── Duplicado y capacidad ──────────────────────────────────────────────────

    @Test
    void execute_enrollmentActivoDuplicado_lanzaPlayerAlreadyEnrolledSinContarNiGuardar() {
        ClassEnrollment active = new ClassEnrollment(55L, SLOT_ID, PLAYER_ID, true);
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slotWithCapacity(4)));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(classEnrollmentRepository.findByClassSlotIdAndPlayerId(SLOT_ID, PLAYER_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID))
                .isInstanceOf(PlayerAlreadyEnrolledException.class);

        verify(classEnrollmentRepository, never()).countActiveByClassSlotId(any());
        verify(classEnrollmentRepository, never()).save(any());
    }

    @Test
    void execute_grupoLleno_lanzaClassSlotFullSinGuardar() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slotWithCapacity(4)));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(classEnrollmentRepository.findByClassSlotIdAndPlayerId(SLOT_ID, PLAYER_ID)).thenReturn(Optional.empty());
        when(classEnrollmentRepository.countActiveByClassSlotId(SLOT_ID)).thenReturn(4);

        assertThatThrownBy(() -> useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID))
                .isInstanceOf(ClassSlotFullException.class);

        verify(classEnrollmentRepository, never()).save(any());
    }

    // ─── Orden de lock ──────────────────────────────────────────────────────────

    @Test
    void execute_tomaElLockDeClassSlotAntesDeCualquierOtraLectura() {
        when(classSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slotWithCapacity(4)));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(classEnrollmentRepository.findByClassSlotIdAndPlayerId(SLOT_ID, PLAYER_ID)).thenReturn(Optional.empty());
        when(classEnrollmentRepository.countActiveByClassSlotId(SLOT_ID)).thenReturn(0);
        when(classEnrollmentRepository.save(any(ClassEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(ownerInstructor(), SLOT_ID, PLAYER_ID);

        InOrder order = inOrder(classSlotRepository, playerGateway, classEnrollmentRepository);
        order.verify(classSlotRepository).findByIdForUpdate(SLOT_ID);
        order.verify(playerGateway).findById(PLAYER_ID);
        order.verify(classEnrollmentRepository).save(any(ClassEnrollment.class));
    }
}
