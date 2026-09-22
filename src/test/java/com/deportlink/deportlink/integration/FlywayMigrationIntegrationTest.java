package com.deportlink.deportlink.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nivel C — migración desde una base vacía (ver docs/class-management-stage-1c-persistence-
 * design.md, sección 9.3). El propio arranque del contexto YA es la prueba principal:
 * spring.flyway.enabled=true + ddl-auto=validate significa que si V1..V9 no coinciden
 * exactamente con las entidades JPA, el contexto no levanta — no hace falta ninguna aserción
 * adicional para eso. Los métodos de este test verifican, además, el estado final concreto.
 */
class FlywayMigrationIntegrationTest extends MySqlFlywayIntegrationTestBase {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads_flywayCorrioTodasLasMigracionesYElEsquemaValidaContraLasEntidades() {
        Integer appliedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        // V1..V9 = 9 migraciones versionadas (V1 se marca aplicada por baseline-on-migrate, no
        // ejecutada — igual cuenta como fila "success" en el historial).
        assertTrue(appliedCount != null && appliedCount >= 9,
                "Se esperaban al menos 9 migraciones aplicadas exitosamente, hubo " + appliedCount);
    }

    @Test
    void tablasNuevasExistenConLasColumnasEsperadas() {
        for (String table : new String[]{"instructors", "class_slot", "class_enrollment",
                "class_session", "class_attendance", "court_occupancy"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class, table);
            assertEquals(1, count, "Falta la tabla " + table);
        }
    }

    @Test
    void classSessionNoTieneColumnaCourtId() {
        // Decisión cerrada de la sección 3.4 del diseño de 1C — confirmado contra el esquema real,
        // no solo contra la entidad Java.
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE table_schema = DATABASE() AND table_name = 'class_session' AND column_name = 'court_id'
        """, Integer.class);

        assertEquals(0, count, "class_session no debe tener columna court_id (decisión cerrada, sección 3.4)");
    }

    @Test
    void usersRoleIncluyeInstructor() {
        String columnType = jdbcTemplate.queryForObject("""
            SELECT COLUMN_TYPE FROM information_schema.COLUMNS
            WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'role'
        """, String.class);

        assertTrue(columnType != null && columnType.contains("INSTRUCTOR"),
                "users.role debe incluir 'INSTRUCTOR', fue: " + columnType);
    }
}
