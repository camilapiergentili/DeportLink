package com.deportlink.deportlink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Paquete nuevo, separado de security/config y de WebConfig — Clock no es un concepto de
 * seguridad ni de configuración web (decisión cerrada, ver
 * docs/class-management-stage-1c-persistence-design.md, sección 4.1).
 * <p>
 * Clock.systemDefaultZone() usa la misma zona que LocalDateTime.now()/LocalDate.now() ya usan
 * implícitamente en todo el resto del proyecto (Reservation.create(), TimeSlot.isFuture(),
 * ClassSession.create()) — no introduce ninguna discrepancia nueva. La ambigüedad de fondo
 * (ninguna zona horaria fijada explícitamente en ningún lado del proyecto) es preexistente y no
 * se resuelve acá.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
