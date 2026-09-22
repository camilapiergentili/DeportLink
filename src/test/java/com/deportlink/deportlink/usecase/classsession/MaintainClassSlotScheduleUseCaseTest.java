package com.deportlink.deportlink.usecase.classsession;

import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.port.out.ClassSlotCourtGateway;
import com.deportlink.deportlink.application.usecase.classsession.*;
import com.deportlink.deportlink.domain.model.ClassSlot;
import com.deportlink.deportlink.domain.port.out.*;
import com.deportlink.deportlink.enums.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class MaintainClassSlotScheduleUseCaseTest {
    private final ClassSlotCourtGateway courts = mock(ClassSlotCourtGateway.class);
    private final ClassSlotRepositoryPort slots = mock(ClassSlotRepositoryPort.class);
    private final ClassSessionRepositoryPort sessions = mock(ClassSessionRepositoryPort.class);
    private final CreateClassSessionUseCase creator = mock(CreateClassSessionUseCase.class);
    private final Clock clock = Clock.fixed(Instant.parse("2030-01-03T12:00:00Z"), ZoneOffset.UTC); // Thursday
    private final MaintainClassSlotScheduleUseCase useCase =
            new MaintainClassSlotScheduleUseCase(courts, slots, sessions, creator, clock);
    private ClassSlot slot(ActiveStatus status, int hour) {
        return new ClassSlot(1L, 2L, 3L, DayOfWeek.THURSDAY, LocalTime.of(hour, 0),
                Duration.ofHours(1), Level.INTERMEDIO, 4, status);
    }
    @BeforeEach void setup() {
        when(courts.findCourtIdByClassSlotForUpdate(1L)).thenReturn(Optional.of(3L));
    }
    @Test void createsFourUpcomingThursdaysAndLocksBeforeReading() {
        when(slots.findByIdForUpdate(1L)).thenReturn(Optional.of(slot(ActiveStatus.ACTIVE, 15)));
        assertEquals(4, useCase.execute(1L));
        var order = inOrder(courts, slots, sessions, creator);
        order.verify(courts).findCourtIdByClassSlotForUpdate(1L);
        order.verify(slots).findByIdForUpdate(1L);
        for (int week = 0; week < 4; week++) {
            LocalDate date = LocalDate.of(2030, 1, 3).plusWeeks(week);
            order.verify(sessions).existsByClassSlotIdAndDay(1L, date);
            order.verify(creator).execute(any(Actor.class), eq(1L), eq(date));
        }
    }
    @Test void skipsExistingSessionsWithoutRewritingTheirAttendances() {
        when(slots.findByIdForUpdate(1L)).thenReturn(Optional.of(slot(ActiveStatus.ACTIVE, 15)));
        when(sessions.existsByClassSlotIdAndDay(eq(1L), any())).thenReturn(true);
        assertEquals(0, useCase.execute(1L));
        verifyNoInteractions(creator);
    }
    @Test void afterClassStartsBeginsNextWeekAndNeverBackfillsPastDates() {
        when(slots.findByIdForUpdate(1L)).thenReturn(Optional.of(slot(ActiveStatus.ACTIVE, 10)));
        useCase.execute(1L);
        verify(creator, never()).execute(any(), any(), eq(LocalDate.of(2030, 1, 3)));
        verify(creator).execute(any(), eq(1L), eq(LocalDate.of(2030, 1, 31)));
    }
    @Test void pausedSchedulesDoNotCreateAnything() {
        when(slots.findByIdForUpdate(1L)).thenReturn(Optional.of(slot(ActiveStatus.INACTIVE, 15)));
        assertEquals(0, useCase.execute(1L));
        verifyNoInteractions(sessions, creator);
    }
    @Test void advancingOneWeekAddsOnlyTheNewFourthOccurrence() {
        when(slots.findByIdForUpdate(1L)).thenReturn(Optional.of(slot(ActiveStatus.ACTIVE, 15)));
        LocalDate first = LocalDate.of(2030, 1, 10);
        for (int week = 0; week < 3; week++) {
            when(sessions.existsByClassSlotIdAndDay(1L, first.plusWeeks(week))).thenReturn(true);
        }
        var nextWeek = new MaintainClassSlotScheduleUseCase(courts, slots, sessions, creator,
                Clock.fixed(Instant.parse("2030-01-10T12:00:00Z"), ZoneOffset.UTC));
        assertEquals(1, nextWeek.execute(1L));
        verify(creator).execute(any(), eq(1L), eq(LocalDate.of(2030, 1, 31)));
        verifyNoMoreInteractions(creator);
    }
    @Test void failurePropagatesForTransactionRollback() {
        when(slots.findByIdForUpdate(1L)).thenReturn(Optional.of(slot(ActiveStatus.ACTIVE, 15)));
        when(creator.execute(any(), any(), any())).thenThrow(new IllegalStateException("write failed"));
        assertThrows(IllegalStateException.class, () -> useCase.execute(1L));
    }
}
