package io.github.xiaowuiot.jt.server.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static <E, T> PageResult<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResult<>(page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
