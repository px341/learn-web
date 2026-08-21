package com.learn.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 跨服务复用的分页请求，页码从 0 开始，单页最多 100 条。
 */
@Data
public class PageQueryDTO {

    @Min(0)
    private int page;

    @Min(1)
    @Max(100)
    private int size;

    public PageQueryDTO() {
    }

    public PageQueryDTO(int page, int size) {
        this.page = page;
        this.size = size;
    }

    public static PageQueryDTO defaultQuery() {
        return new PageQueryDTO(0, 20);
    }
}
