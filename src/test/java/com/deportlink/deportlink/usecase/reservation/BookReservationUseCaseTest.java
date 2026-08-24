package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.CourtGateway.CourtSnapshot;
import com.deportlink.deportlink.application.port.out.PlayerGateway;
import com.deportlink.deportlink.application.port.out.PlayerGateway.PlayerSnapshot;
import com.deportlink.deportlink.application.port.out.ScheduleGateway;
import com.deportlink.deportlink.application.port.out.ScheduleGateway.SlotConfig;
import com.deportlink.deportlink.application.usecase.reservation.BookReservationUseCase;
import com.deportlink.deportlink.domain.model.Reservation;
import com.deportlink.deportlink.domain.model.TimeSlot;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.enums.StatusReservation;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import com.deportlink.deportlink.exception.SlotNotAvailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Primera tanda de tests de BookReservationUseCase, según la matriz de auditoría (11 reglas).
 * Cubre: happy path + lock pesimista (H1, H5, H6) y casos negativos de entidades
 * inexistentes / slot inválido / slot ocupado (N1-N5).
 * Próximas tandas: H2, cancelación, reschedule, concurrencia real, tests de infraestructura.
 */
@ExtendWith(MockitoExtension.class)
class BookReservationUseCaseTest {

    @Mock private ReservationRepositoryPort reservationRepository;
    @Mock private CourtGateway courtGateway;
    @Mock private PlayerGateway playerGateway;
    @Mock private ScheduleGateway scheduleGateway;

    @InjectMocks private BookReservationUseCase useCase;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static final Long COURT_ID = 1L;
    private static final Long PLAYER_ID = 2L;
    private static final LocalDate DAY = LocalDate.now().plusDays(3);
    private static final LocalTime START_TIME = LocalTime.of(10, 0);

    private static CourtSnapshot court() {
        return new CourtSnapshot(COURT_ID, 100.0, "Cancha 1", "Fútbol", "Sucursal Centro", "Av. Siempre Viva 123", 12);
    }

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(PLAYER_ID, "Juan", "Pérez");
    }

    private static SlotConfig slotConfig() {
        // 09:00 a 13:00, turnos de 1 hora -> 10:00 es un slot válido.
        return new SlotConfig(LocalTime.of(9, 0), LocalTime.of(13, 0), Duration.ofHours(1));
    }

    private static Reservation savedReservation(TimeSlot timeSlot) {
        return new Reservation(99L, COURT_ID, PLAYER_ID, timeSlot, StatusReservation.RESERVADO, null)
                .withTicket(null); // el id del ticket lo asigna el adapter real; acá alcanza con id de Reservation
    }

    // ─── HAPPY PATH ─────────────────────────────────────────────────────────────

    @Test
    void execute_reservaValida_devuelveReservationConTicketYPrecioCorrecto() {
        TimeSlot expectedSlot = new TimeSlot(DAY, START_TIME, Duration.ofHours(1));
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        Reservation persisted = savedReservation(expectedSlot);

        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of());
        when(reservationRepository.save(captor.capture())).thenReturn(persisted);

        Reservation result = useCase.execute(COURT_ID, PLAYER_ID, DAY, START_TIME);

        // resultado correcto
        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.status()).isEqualTo(StatusReservation.RESERVADO);
        assertThat(result.courtId()).isEqualTo(COURT_ID);
        assertThat(result.playerId()).isEqualTo(PLAYER_ID);
        assertThat(result.timeSlot()).isEqualTo(expectedSlot);

        // reservation creada con los datos correctos antes de persistir (precio, ticket, timeSlot)
        Reservation toPersist = captor.getValue();
        assertThat(toPersist.id()).isNull();
        assertThat(toPersist.status()).isEqualTo(StatusReservation.RESERVADO);
        assertThat(toPersist.timeSlot()).isEqualTo(expectedSlot);
        assertThat(toPersist.ticket()).isNotNull();
        assertThat(toPersist.ticket().totalPrice()).isEqualTo(100.0); // 1 hora x $100/h
        assertThat(toPersist.ticket().playerName()).isEqualTo("Juan");
        assertThat(toPersist.ticket().playerLastName()).isEqualTo("Pérez");
        assertThat(toPersist.ticket().courtName()).isEqualTo("Cancha 1");
        assertThat(toPersist.ticket().sport()).isEqualTo("Fútbol");
        assertThat(toPersist.ticket().branchName()).isEqualTo("Sucursal Centro");
        assertThat(toPersist.ticket().branchAddress()).isEqualTo("Av. Siempre Viva 123");

        // dependencias llamadas correctamente
        verify(courtGateway).findByIdForUpdate(COURT_ID);
        verify(playerGateway).findById(PLAYER_ID);
        verify(scheduleGateway).findByCourtAndDay(COURT_ID, DAY.getDayOfWeek());
        verify(reservationRepository).findBookedSlots(COURT_ID, DAY);
        verify(reservationRepository, times(1)).save(any(Reservation.class));

        // comportamiento del lock: se usa el método con lock, no el findById plano
        verify(courtGateway, never()).findById(any());
    }

    @Test
    void execute_usaFindByIdForUpdateParaAdquirirElLockSobreLaCancha() {
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of());
        when(reservationRepository.save(any())).thenReturn(savedReservation(new TimeSlot(DAY, START_TIME, Duration.ofHours(1))));

        useCase.execute(COURT_ID, PLAYER_ID, DAY, START_TIME);

        // el mecanismo de lock pasa exactamente por findByIdForUpdate, una única vez, con el courtId correcto
        verify(courtGateway, times(1)).findByIdForUpdate(COURT_ID);
        verify(courtGateway, never()).findById(any());
    }

    @Test
    void execute_adquiereElLockAntesDeVerificarDisponibilidadDelSlot() {
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of());
        when(reservationRepository.save(any())).thenReturn(savedReservation(new TimeSlot(DAY, START_TIME, Duration.ofHours(1))));

        useCase.execute(COURT_ID, PLAYER_ID, DAY, START_TIME);

        // orden real del flujo: lock de la cancha -> lectura de slots ocupados -> persistencia
        InOrder inOrder = inOrder(courtGateway, reservationRepository);
        inOrder.verify(courtGateway).findByIdForUpdate(COURT_ID);
        inOrder.verify(reservationRepository).findBookedSlots(COURT_ID, DAY);
        inOrder.verify(reservationRepository).save(any(Reservation.class));
    }

    // ─── CASOS NEGATIVOS ────────────────────────────────────────────────────────

    @Test
    void execute_courtInexistente_lanzaCourtNotFoundYNoContinuaElFlujo() {
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(COURT_ID, PLAYER_ID, DAY, START_TIME))
                .isInstanceOf(CourtNotFoundException.class)
                .hasMessage("No se encontró la cancha");

        verifyNoInteractions(playerGateway, scheduleGateway, reservationRepository);
    }

    @Test
    void execute_playerInexistente_lanzaPlayerNotFoundYNoContinuaElFlujo() {
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(COURT_ID, PLAYER_ID, DAY, START_TIME))
                .isInstanceOf(PlayerNotFoundException.class)
                .hasMessage("No se encontró el jugador");

        verifyNoInteractions(scheduleGateway, reservationRepository);
    }

    @Test
    void execute_scheduleInexistente_lanzaScheduleNotFoundYNoContinuaElFlujo() {
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(COURT_ID, PLAYER_ID, DAY, START_TIME))
                .isInstanceOf(ScheduleNotFoundException.class)
                .hasMessage("No hay agenda disponible para ese día");

        verifyNoInteractions(reservationRepository);
    }

    @Test
    void execute_slotInvalido_lanzaSlotNotAvailableYNoPersisteNada() {
        LocalTime invalidTime = LocalTime.of(8, 30); // fuera del rango de la agenda (09:00-13:00)

        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));

        assertThatThrownBy(() -> useCase.execute(COURT_ID, PLAYER_ID, DAY, invalidTime))
                .isInstanceOf(SlotNotAvailableException.class)
                .hasMessage("El horario no corresponde a un turno válido");

        // ni siquiera llega a consultar los slots ocupados
        verify(reservationRepository, never()).findBookedSlots(any(), any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void execute_slotOcupado_lanzaSlotNotAvailableYNoCreaSegundaReservation() {
        when(courtGateway.findByIdForUpdate(COURT_ID)).thenReturn(Optional.of(court()));
        when(playerGateway.findById(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of(START_TIME));

        assertThatThrownBy(() -> useCase.execute(COURT_ID, PLAYER_ID, DAY, START_TIME))
                .isInstanceOf(SlotNotAvailableException.class)
                .hasMessage("El horario ya está reservado");

        verify(reservationRepository, never()).save(any());
    }
}