package com.deportlink.deportlink.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dispara {@link DevDataSeeder} al arrancar, solo con el perfil {@code dev} activo
 * ({@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev} — ver README). Nunca corre en
 * staging/producción: ahí no se activa ese perfil, y sin él este bean ni siquiera se registra
 * en el contexto de Spring.
 * <p>
 * Separado de DevDataSeeder a propósito: {@code @Transactional} en {@link DevDataSeeder#seed()}
 * solo funciona a través del proxy de Spring, y una llamada desde el método run() de esta misma
 * clase (auto-invocación) lo saltearía. Al ser dos beans distintos, la llamada sí pasa por el
 * proxy y una falla a mitad de camino revierte todo en vez de dejar datos a medias.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeederRunner implements CommandLineRunner {

    private final DevDataSeeder devDataSeeder;

    @Override
    public void run(String... args) {
        devDataSeeder.seed();
    }
}
