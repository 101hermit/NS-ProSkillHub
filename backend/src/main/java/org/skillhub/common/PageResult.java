package org.skillhub.common;

import java.util.List;

/**
 * 分页出参信封：{@code {page, size, total, total_pages, items}}。
 */
public record PageResult<T>(int page, int size, long total, int totalPages, List<T> items) {

    public static <T> PageResult<T> of(PageQuery query, long total, List<T> items) {
        int size = query.safeSize();
        int pages = size == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResult<>(query.safePage(), size, total, pages, items);
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(query.safePage(), query.safeSize(), 0, 0, List.of());
    }
}
