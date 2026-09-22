package com.deportlink.deportlink.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nivel C — "actualización desde V3 con reservas de distintos estados" (ver
 * docs/class-management-stage-1c-persistence-design.md, sección 9.3). Deliberadamente SIN
 * @SpringBootTest: Spring Boot correría automáticamente V1..V9 al arrancar el contexto, sin
 * dejar margen para insertar datos "en estado V3" antes de V4-V9. Se usa la API programática de
 * Flyway directamente contra un DataSource JDBC plano — mismo mecanismo, dos llamadas a
 * .migrate() con distinto target.
 * <p>
 * Verifica en particular: el backfill de V9 solo toma reservas RESERVADO — nunca
 * CANCELADO/FINALIZADO/REPROGRAMADO (sección 8.1 del diseño).
 */
@Testcontainers
class BackfillFromV3IntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.23")
            .withDatabaseName("deportlink_backfill")
            .withUsername("test")
            .withPassword("test");

    @Test
    void backfill_soloTomaReservasQueOcupanCancha_noLasCanceladasNiFinalizadasNiReprogramadas() throws Exception {
        Flyway flywayToV3 = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .target("3")
                .load();
        flywayToV3.migrate();

        try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement()) {

            // Datos mínimos para poder insertar una reservation válida (FKs reales).
            statement.execute("INSERT INTO sport (id, name_sport) VALUES (1, 'Pádel')");
            statement.execute("INSERT INTO users (id, first_name, last_name, email, password, role) " +
                    "VALUES (1, 'Test', 'Owner', 'owner-backfill@example.com', 'x', 'OWNER')");
            statement.execute("INSERT INTO owners (id, dni) VALUES (1, 20123456789)");
            statement.execute("INSERT INTO clubs (id, name, legal_name, cuit) VALUES (1, 'Club', 'Club Legal', '30111111111')");
            statement.execute("INSERT INTO owner_club (owner_id, club_id) VALUES (1, 1)");
            statement.execute("INSERT INTO branches (id, name, club_id, cancellation_window_hours) VALUES (1, 'Sucursal', 1, 12)");
            statement.execute("INSERT INTO court (id, name, price_per_hour, branch_id, sport_id) VALUES (1, 'Cancha 1', 1000, 1, 1)");
            statement.execute("INSERT INTO users (id, first_name, last_name, email, password, role) " +
                    "VALUES (2, 'Test', 'Player', 'player-backfill@example.com', 'x', 'PLAYER')");
            statement.execute("INSERT INTO players (id) VALUES (2)");

            // Cuatro reservas, una por cada estado — misma cancha, horarios distintos para no
            // chocar con uq_reservation_active_slot (que solo aplica a RESERVADO de cualquier modo).
            statement.execute("""
                INSERT INTO reservation (id, reservation_day, start_time, duration, status, court_id, player_id) VALUES
                (1, '2026-12-03', '10:00:00', 60, 'RESERVADO', 1, 2),
                (2, '2026-12-03', '11:00:00', 60, 'CANCELADO', 1, 2),
                (3, '2026-12-03', '12:00:00', 60, 'FINALIZADO', 1, 2),
                (4, '2026-12-03', '13:00:00', 60, 'REPROGRAMADO', 1, 2)
            """);

            Flyway flywayToLatest = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .locations("classpath:db/migration")
                    .target("latest")
                    .load();
            flywayToLatest.migrate();

            try (ResultSet rs = statement.executeQuery("SELECT source_id FROM court_occupancy WHERE source_type = 'RESERVATION'")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertEquals(1L, rs.getLong("source_id"),
                            "Solo la reserva RESERVADO (id=1) debe haberse volcado a court_occupancy");
                }
                assertEquals(1, count, "Debe haber exactamente una fila de ocupación — las otras 3 reservas no ocupan cancha");
            }
        }
    }
}
