package com.learn.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 跨服务复用的分页请求，页码从 0 开始，单页最多 100 条。
 *
 * <p>字段约束由 Bean Validation 执行。Controller 接收该对象时应配合
 * {@code @Valid} 或 {@code @Validated}，否则 {@link Min}、{@link Max}
 * 注解不会自动拒绝非法参数。</p>
 */
@Data
public class PageQueryDTO {

    /** 从 0 开始的页码。 */
    @Min(0)
    private int page;

    /** 每页记录数，合法范围为 1～100。 */
    @Min(1)
    @Max(100)
    private int size;

    /**
     * 供 Spring MVC 等数据绑定框架使用的无参构造方法。
     *
     * <p>该构造方法不会填充默认分页大小；需要业务默认值时应使用
     * {@link #defaultQuery()}。</p>
     */
    public PageQueryDTO() {
    }

    /**
     * 创建指定页码和分页大小的查询参数。
     *
     * @param page 从 0 开始的页码
     * @param size 每页记录数，合法范围为 1～100
     */
    public PageQueryDTO(int page, int size) {
        this.page = page;
        this.size = size;
    }

    /**
     * 创建项目约定的默认分页参数。
     *
     * @return 第 0 页、每页 20 条的分页参数
     */
    public static PageQueryDTO defaultQuery() {
        return new PageQueryDTO(0, 20);
    }
}
