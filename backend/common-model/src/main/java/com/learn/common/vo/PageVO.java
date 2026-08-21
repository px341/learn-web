package com.learn.common.vo;

import lombok.Getter;

import java.util.List;

/**
 * 跨服务通用的分页响应。
 */
@Getter
public class PageVO<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public PageVO(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        this.items = items == null ? List.of() : List.copyOf(items);
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }
}