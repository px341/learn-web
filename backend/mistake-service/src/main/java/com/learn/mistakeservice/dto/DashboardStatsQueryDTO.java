package com.learn.mistakeservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Dashboard 统计查询参数。
 *
 * <p>{@code days} 只控制正确率趋势的时间范围。其他卡片指标按接口定义的固定统计口径
 * 计算，客户端不能提交用户 ID；用户身份必须从已验证 JWT 中获取。</p>
 */
public record DashboardStatsQueryDTO(
        @NotNull(message = "统计天数不能为空")
        Integer days
) {

    public static final int DEFAULT_DAYS = 7;
    private static final Set<Integer> SUPPORTED_DAYS = Set.of(7, 14, 30);

    /** Spring MVC 未收到 days 参数时使用接口约定的默认值。 */
    public DashboardStatsQueryDTO() {
        this(DEFAULT_DAYS);
    }

    /**
     * 创建并校验查询参数。
     *
     * @throws IllegalArgumentException days 不是 7、14 或 30 时抛出
     */
    public DashboardStatsQueryDTO {
        if (days == null) {
            days = DEFAULT_DAYS;
        }
        if (!SUPPORTED_DAYS.contains(days)) {
            throw new IllegalArgumentException("统计天数只支持 7、14 或 30");
        }
    }
}
