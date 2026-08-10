package com.deportlink.deportlink.domain.port.out;

import java.util.List;

/**
 * Resultado paginado agnóstico de framework, devuelto por los output ports que exponen
 * listados paginados. Los adapters de infrastructure/ lo construyen a partir de un
 * Page&lt;Entity&gt; de Spring Data.
 */
public record PageResult<T>(List<T> content, long totalElements, int totalPages, int pageNumber) {

    public static <T> PageResult<T> of(List<T> content, long totalElements, int totalPages, int pageNumber) {
        return new PageResult<>(content, totalElements, totalPages, pageNumber);
    }
}
