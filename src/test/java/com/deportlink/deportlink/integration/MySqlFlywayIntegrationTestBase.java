package com.deportlink.deportlink.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base de los tests de Nivel C (ver docs/class-management-stage-1c-persistence-design.md,
 * sección 9.3) — MySQL real vía Testcontainers, con Flyway HABILITADO y
 * ddl-auto=validate. Distinto, a propósito, del molde de las 4 clases de concurrencia
 * preexistentes (BookReservationConcurrencyTest y hermanas), que deshabilitan Flyway y usan
 * ddl-auto=create-drop — ese molde prueba que el código serializa correctamente contra un
 * esquema generado por Hibernate desde las entidades, no que las migraciones V1–V9 son
 * correctas. Acá se ejercitan las migraciones reales de punta a punta.
 * <p>
 * No se reutiliza ni se modifica el molde existente (ConcurrencyTestFixtures / las 4 clases ya
 * probadas) — es un test harness nuevo y separado para este módulo, según lo pedido
 * explícitamente ("no ampliar tests de otros módulos por conveniencia").
 */
@Testcontainers
@SpringBootTest
// Each class restarts the container; do not reuse connections to its previous address.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MySqlFlywayIntegrationTestBase {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.23")
            .withDatabaseName("deportlink_1c")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        // Override the H2 dialect inherited from test application.properties.
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");

        // A diferencia de las 4 clases de concurrencia existentes: Flyway CORRE de verdad
        // (V1..V9) y Hibernate solo VALIDA contra lo que esas migraciones crearon — mismo
        // comportamiento que application.properties usa en producción.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LWxvbmctZW5vdWdoLWZvci1qd3Qtc2lnbmluZw==");
        registry.add("jwt.expiration", () -> "36000000");
        registry.add("cors.allowed-origins", () -> "http://localhost:5173");
    }
}
