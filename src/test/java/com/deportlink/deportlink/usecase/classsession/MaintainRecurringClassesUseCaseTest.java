package com.deportlink.deportlink.usecase.classsession;
import com.deportlink.deportlink.application.port.out.ClassRecurrencePort;
import com.deportlink.deportlink.application.usecase.classsession.*;
import com.deportlink.deportlink.infrastructure.scheduling.RecurringClassesScheduler;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MaintainRecurringClassesUseCaseTest {
    @Test void failureInOneScheduleDoesNotStopOthersAndIsReportedForRetry() {
        var port = mock(ClassRecurrencePort.class);
        var fill = mock(MaintainClassSlotScheduleUseCase.class);
        when(port.findActiveSlotIds()).thenReturn(List.of(1L, 2L));
        when(fill.execute(1L)).thenThrow(new IllegalStateException("conflict"));
        when(fill.execute(2L)).thenReturn(4);
        var result = new MaintainRecurringClassesUseCase(port, fill).execute();
        assertEquals(4, result.created());
        assertEquals(Set.of(1L), result.failures().keySet());
        verify(fill).execute(2L);
    }
    @Test void startupAndDailyTriggerUseSameRecoveryOperation() {
        var maintain = mock(MaintainRecurringClassesUseCase.class);
        when(maintain.execute()).thenReturn(new MaintainRecurringClassesUseCase.Report(0, Map.of()));
        var scheduler = new RecurringClassesScheduler(maintain);
        scheduler.onReady();
        scheduler.run();
        verify(maintain, times(2)).execute();
    }
}
