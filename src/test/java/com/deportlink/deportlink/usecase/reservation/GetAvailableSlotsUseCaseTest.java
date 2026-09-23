package com.deportlink.deportlink.usecase.reservation;

import com.deportlink.deportlink.application.port.out.CourtGateway;
import com.deportlink.deportlink.application.port.out.CourtGateway.CourtSnapshot;
import com.deportlink.deportlink.application.port.out.ScheduleGateway;
import com.deportlink.deportlink.application.port.out.ScheduleGateway.SlotConfig;
import com.deportlink.deportlink.application.usecase.reservation.GetAvailableSlotsUseCase;
import com.deportlink.deportlink.domain.port.out.ReservationRepositoryPort;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.ScheduleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetAvailableSlotsUseCaseTest {
    @Mock private com.deportlink.deportlink.application.port.out.ClassRecurrencePort classRecurrence;
    @Mock private com.deportlink.deportlink.application.port.out.CourtOccupancyPort courtOccupancy;

    @Mock private CourtGateway courtGateway;
    @Mock private ScheduleGateway scheduleGateway;
    @Mock private ReservationRepositoryPort reservationRepository;

    // Construido a mano en vez de con @InjectMocks: sin un @Mock Clock declarado, @InjectMocks
    // pasaría null como Clock (deja sin resolver los parámetros del constructor que no matchean
    // ningún mock) y el primer LocalDate.now(clock) explotaría con NPE. Además cada test de N3
    // necesita un Clock propio (el de setUp() sirve para los que no ejercen ese filtro).
    private GetAvailableSlotsUseCase useCase;

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static final Long COURT_ID = 10L;
    private static final LocalDate DAY = LocalDate.of(2026, 8, 31); // lunes
    private static final ZoneId ZONE = ZoneId.systemDefault();

    // "Ahora" fijo y anterior a DAY — para los tests que no ejercen el filtro de N3, DAY
    // siempre queda en el futuro respecto de este reloj y el comportamiento es el de antes.
    private static final Clock BEFORE_DAY = Clock.fixed(DAY.minusDays(1).atStartOfDay(ZONE).toInstant(), ZONE);

    @BeforeEach
    void setUp() {
        useCase = new GetAvailableSlotsUseCase(courtGateway, scheduleGateway, reservationRepository, classRecurrence, courtOccupancy, BEFORE_DAY);
    }

    private static CourtSnapshot court() {
        return new CourtSnapshot(COURT_ID, 100.0, "Cancha 1", "Fútbol", "Sucursal Centro",
                "Av. Siempre Viva 123", 12);
    }

    /** Agenda 09:00–13:00 en slots de 1h — genera 09, 10, 11, 12. */
    private static SlotConfig slotConfig() {
        return new SlotConfig(LocalTime.of(9, 0), LocalTime.of(13, 0), Duration.ofHours(1));
    }

    private static Clock fixedAt(LocalDate day, LocalTime time) {
        return Clock.fixed(day.atTime(time).atZone(ZONE).toInstant(), ZONE);
    }

    // ─── Casos negativos ────────────────────────────────────────────────────────

    @Test
    void execute_courtInexistente_lanzaCourtNotFoundYNoContinuaElFlujo() {
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(COURT_ID, DAY))
                .isInstanceOf(CourtNotFoundException.class)
                .hasMessage("No se encontró la cancha");

        verifyNoInteractions(scheduleGateway, reservationRepository);
    }

    @Test
    void execute_sinAgendaParaEseDia_lanzaScheduleNotFound() {
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(COURT_ID, DAY))
                .isInstanceOf(ScheduleNotFoundException.class)
                .hasMessage("No hay agenda disponible para ese día");

        verify(reservationRepository, never()).findBookedSlots(any(), any());
    }

    // ─── Happy path ─────────────────────────────────────────────────────────────

    @Test
    void execute_sinReservas_devuelveTodosLosSlots() {
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of());

        List<LocalTime> result = useCase.execute(COURT_ID, DAY);

        assertThat(result).containsExactly(
                LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0));
    }

    @Test
    void execute_diaCompletamenteReservado_devuelveListaVacia() {
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of(
                LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0)));

        List<LocalTime> result = useCase.execute(COURT_ID, DAY);

        assertThat(result).isEmpty();
    }

    @Test
    void execute_diaParcialmenteReservado_devuelveSoloLosSlotsLibres() {
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of(LocalTime.of(10, 0)));

        List<LocalTime> result = useCase.execute(COURT_ID, DAY);

        assertThat(result).containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 0), LocalTime.of(12, 0));
    }

    // ─── N3: turnos pasados no deben figurar como disponibles ────────────────────

    @Test
    void execute_diaEnteramenteEnElPasado_devuelveListaVaciaSinConsultarReservas() {
        useCase = new GetAvailableSlotsUseCase(courtGateway, scheduleGateway, reservationRepository, classRecurrence, courtOccupancy,
                fixedAt(DAY.plusDays(1), LocalTime.of(10, 0))); // "hoy" es un día después de DAY
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));

        List<LocalTime> result = useCase.execute(COURT_ID, DAY);

        assertThat(result).isEmpty();
        // Antes de gastar una consulta a reservas, ya sabemos que ningún slot de un día pasado
        // puede ser válido — no tiene sentido preguntarle al repositorio qué está ocupado.
        verify(reservationRepository, never()).findBookedSlots(any(), any());
    }

    @Test
    void execute_hoyConHoraYaPasada_excluyeLosSlotsAnterioresAAhoraPeroMantieneLosFuturos() {
        // "Ahora" son las 10:30 de DAY: el slot de las 09:00 y el de las 10:00 ya arrancaron.
        useCase = new GetAvailableSlotsUseCase(courtGateway, scheduleGateway, reservationRepository, classRecurrence, courtOccupancy,
                fixedAt(DAY, LocalTime.of(10, 30)));
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of());

        List<LocalTime> result = useCase.execute(COURT_ID, DAY);

        assertThat(result).containsExactly(LocalTime.of(11, 0), LocalTime.of(12, 0));
    }

    @Test
    void execute_hoyExactamenteALaHoraDeUnSlot_incluyeEseSlot() {
        // Empieza en este instante — todavía se puede reservar, no arrancó.
        useCase = new GetAvailableSlotsUseCase(courtGateway, scheduleGateway, reservationRepository, classRecurrence, courtOccupancy,
                fixedAt(DAY, LocalTime.of(11, 0)));
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of());

        List<LocalTime> result = useCase.execute(COURT_ID, DAY);

        assertThat(result).containsExactly(LocalTime.of(11, 0), LocalTime.of(12, 0));
    }

    @Test
    void execute_hoyDespuesDeTodosLosSlots_devuelveListaVacia() {
        useCase = new GetAvailableSlotsUseCase(courtGateway, scheduleGateway, reservationRepository, classRecurrence, courtOccupancy,
                fixedAt(DAY, LocalTime.of(23, 0)));
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of());

        List<LocalTime> result = useCase.execute(COURT_ID, DAY);

        assertThat(result).isEmpty();
    }

    @Test
    void execute_unDiaFuturo_noFiltraPorHoraAunqueSeaMuyTarde() {
        // "Ahora" son las 23:59 del día ANTERIOR a DAY: DAY sigue siendo un día futuro completo,
        // así que ningún slot de DAY se filtra por hora.
        useCase = new GetAvailableSlotsUseCase(courtGateway, scheduleGateway, reservationRepository, classRecurrence, courtOccupancy,
                fixedAt(DAY.minusDays(1), LocalTime.of(23, 59)));
        when(courtGateway.findById(COURT_ID)).thenReturn(Optional.of(court()));
        when(scheduleGateway.findByCourtAndDay(COURT_ID, DAY.getDayOfWeek())).thenReturn(Optional.of(slotConfig()));
        when(reservationRepository.findBookedSlots(COURT_ID, DAY)).thenReturn(Set.of());

        List<LocalTime> result = useCase.execute(COURT_ID, DAY);

        assertThat(result).containsExactly(
                LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0));
    }
}
