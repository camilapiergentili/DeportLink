package com.deportlink.deportlink.integration;

import com.deportlink.deportlink.domain.port.out.ClassAttendanceRepositoryPort;
import com.deportlink.deportlink.enums.ClassAttendanceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Only the final attendance write fails artificially; session and occupancy writes use MySQL. */
class ClassSessionRollbackIntegrationTest extends OccupancyLifecycleFixture {
    @MockitoBean private ClassAttendanceRepositoryPort attendanceRepository;

    @Test
    void falloAlGuardarAsistencias_revierteSesionYOcupacion() {
        var failure = new IllegalStateException("Fallo de asistencia simulado");
        // Observe the actual inserts inside the service transaction before forcing rollback.
        doAnswer(invocation -> {
            assertEquals(1L, count("SELECT COUNT(*) FROM class_session WHERE class_slot_id = ?", slotId));
            assertEquals(1L, count("SELECT COUNT(*) FROM court_occupancy WHERE court_id = ?", courtId));
            throw failure;
        }).when(attendanceRepository).saveAll(anyList());

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> createSession.execute(admin, slotId, day)));

        verify(attendanceRepository).saveAll(argThat(rows -> rows.size() == 1
                && rows.getFirst().playerId().equals(playerId)
                && rows.getFirst().classSessionId() != null
                && rows.getFirst().status() == ClassAttendanceStatus.PENDING));
        assertEquals(0L, count("SELECT COUNT(*) FROM class_session WHERE class_slot_id = ?", slotId));
        assertEquals(0L, count("SELECT COUNT(*) FROM court_occupancy WHERE court_id = ?", courtId));
        assertEquals(0L, count("SELECT COUNT(*) FROM class_attendance WHERE player_id = ?", playerId));
        assertEquals(1L, count("SELECT COUNT(*) FROM class_enrollment WHERE class_slot_id = ? AND player_id = ? AND active = true", slotId, playerId));
    }
}
