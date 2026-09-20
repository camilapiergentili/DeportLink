package com.deportlink.deportlink.infrastructure.scheduling;

import com.deportlink.deportlink.application.usecase.classsession.MaintainRecurringClassesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Startup recovery + daily replenishment. Uses the same JVM zone as the domain Clock. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "classes.recurrence.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RecurringClassesScheduler {
    private final MaintainRecurringClassesUseCase maintain;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { run(); }

    @Scheduled(cron = "${classes.recurrence.cron:0 5 0 * * *}")
    public void run() {
        var report = maintain.execute();
        log.info("Recurring classes: {} sessions created; failed schedules: {}", report.created(), report.failures());
    }
}
