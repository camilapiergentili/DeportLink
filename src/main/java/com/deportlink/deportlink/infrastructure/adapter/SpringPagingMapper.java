package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.domain.port.out.PageRequest;
import com.deportlink.deportlink.domain.port.out.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.function.Function;

/**
 * Traduce paginación entre el dominio (PageRequest/PageResult) y Spring Data (Pageable/Page).
 * Vive en infrastructure/ porque domain/port/out no debe conocer org.springframework.data.*.
 * Compartida por los adapters paginados para no repetir esta traducción en cada uno.
 */
final class SpringPagingMapper {

    private SpringPagingMapper() {
    }

    static Pageable toSpringPageable(PageRequest pageRequest) {
        // size == Integer.MAX_VALUE es el marcador de PageRequest.unpaged(): usar el sentinel
        // de Spring evita una count query innecesaria en los endpoints sin paginar.
        if (pageRequest.size() == Integer.MAX_VALUE) {
            return Pageable.unpaged();
        }
        Sort sort = pageRequest.sortBy() == null
                ? Sort.unsorted()
                : Sort.by(pageRequest.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, pageRequest.sortBy());
        return org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size(), sort);
    }

    static <E, D> PageResult<D> toPageResult(Page<E> page, Function<E, D> toDomain) {
        return PageResult.of(
                page.getContent().stream().map(toDomain).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber()
        );
    }
}