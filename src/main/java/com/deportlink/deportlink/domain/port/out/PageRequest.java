package com.deportlink.deportlink.domain.port.out;

/**
 * Solicitud de paginación agnóstica de framework, usada por los output ports que exponen
 * listados paginados. Los adapters de infrastructure/ la traducen a Pageable de Spring Data.
 */
public record PageRequest(int page, int size, String sortBy, boolean ascending) {

    public static PageRequest unpaged() {
        return new PageRequest(0, Integer.MAX_VALUE, null, true);
    }
}
