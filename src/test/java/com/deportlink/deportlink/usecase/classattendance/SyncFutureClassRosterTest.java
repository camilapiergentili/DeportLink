package com.deportlink.deportlink.usecase.classattendance;

import com.deportlink.deportlink.application.usecase.classattendance.SyncFutureClassRoster;
import com.deportlink.deportlink.domain.model.*;
import com.deportlink.deportlink.domain.port.out.*;
import com.deportlink.deportlink.enums.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class SyncFutureClassRosterTest {
    private final ClassSessionRepositoryPort sessions = mock(ClassSessionRepositoryPort.class);
    private final ClassAttendanceRepositoryPort attendances = mock(ClassAttendanceRepositoryPort.class);
    private final LocalDateTime now = LocalDateTime.of(2030, 1, 1, 12, 0);
    private final SyncFutureClassRoster sync = new SyncFutureClassRoster(sessions, attendances,
            Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    @BeforeEach void setup() {
        when(sessions.findScheduledAfter(1L, now)).thenReturn(List.of(new ClassSession(2L, 1L,
                now.toLocalDate().plusDays(1), LocalTime.NOON, Duration.ofHours(1), ClassSessionStatus.SCHEDULED)));
    }
    @Test void addingCreatesPendingOnlyForTheNewPlayer() {
        when(attendances.findBySessionAndPlayer(2L, 3L)).thenReturn(Optional.empty());
        sync.execute(1L, 3L, true);
        verify(attendances).save(ClassAttendance.createPending(2L, 3L));
        verify(attendances).findBySessionAndPlayer(2L, 3L);
        verifyNoMoreInteractions(attendances);
    }
    @Test void rejoiningReusesAttendanceIdAndStartsPending() {
        when(attendances.findBySessionAndPlayer(2L, 3L)).thenReturn(
                Optional.of(new ClassAttendance(4L, 2L, 3L, ClassAttendanceStatus.CANCELLED)));
        sync.execute(1L, 3L, true);
        verify(attendances).save(new ClassAttendance(4L, 2L, 3L, ClassAttendanceStatus.PENDING));
    }
    @Test void existingConfirmationIsNotReset() {
        when(attendances.findBySessionAndPlayer(2L, 3L)).thenReturn(
                Optional.of(new ClassAttendance(4L, 2L, 3L, ClassAttendanceStatus.CONFIRMED)));
        sync.execute(1L, 3L, true);
        verify(attendances, never()).save(any());
    }
    @Test void removalCancelsButDoesNotDeleteTheAttendance() {
        when(attendances.findBySessionAndPlayer(2L, 3L)).thenReturn(
                Optional.of(new ClassAttendance(4L, 2L, 3L, ClassAttendanceStatus.CONFIRMED)));
        sync.execute(1L, 3L, false);
        verify(attendances).save(new ClassAttendance(4L, 2L, 3L, ClassAttendanceStatus.CANCELLED));
    }
}
