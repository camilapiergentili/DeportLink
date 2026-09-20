package com.deportlink.deportlink.application.port.out;

import java.util.Map;
import java.util.Set;

/**
 * Fetch en lote de Players por id, para armar el roster de alumnos de una ClassSession
 * (GetClassSessionDetailUseCase) sin una consulta por alumno — separado de {@code PlayerGateway}
 * (que ya tiene adapter de producción con solo {@code findById}) por la misma razón que
 * {@code ClassSlotCourtGateway} está separado de {@code CourtGateway}: agregarle un método batch
 * a un puerto con adapter existente obligaría a implementarlo ahora.
 * <p>
 * Reutiliza {@code PlayerGateway.PlayerSnapshot} como tipo de valor — no se define un snapshot
 * propio para el mismo dato.
 */
public interface PlayerRosterGateway {

    /** Ids sin match no aparecen en el mapa resultante — el caller decide cómo tratarlos. */
    Map<Long, PlayerGateway.PlayerSnapshot> findByIds(Set<Long> playerIds);
}
