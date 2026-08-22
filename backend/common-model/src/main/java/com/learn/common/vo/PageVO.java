package com.learn.common.vo;

import lombok.Getter;

import java.util.List;

/**
 * 跨服务通用的分页响应。
 *
 * <p>响应中的列表会被防御性复制，调用方无法通过修改原列表改变已经创建的分页结果。</p>
 *
 * @param <T> 列表元素类型
 */
@Getter
public class PageVO<T> {

    /** 当前页的数据，只读且永不为 {@code null}。 */
    private final List<T> items;

    /** 当前页码，从 0 开始。 */
    private final int page;

    /** 请求的每页记录数。 */
    private final int size;

    /** 满足查询条件的总记录数。 */
    private final long totalElements;

    /** 根据总记录数和分页大小计算出的总页数。 */
    private final int totalPages;

    /**
     * 创建分页响应。
     *
     * <p>该类型只负责承载分页结果，不会自行校验页码或重新计算总页数。</p>
     *
     * @param items 当前页数据；传入 {@code null} 时转换为空列表
     * @param page 当前页码，从 0 开始
     * @param size 每页记录数
     * @param totalElements 总记录数
     * @param totalPages 总页数
     */
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
