package com.learn.mistakeservice.dto;

import com.learn.mistakeservice.model.AnalysisStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 当前用户错题列表的筛选、分页和排序参数。 */
@Data
public class MistakeListQueryDTO {

    @Size(max = 100, message = "搜索关键词不能超过 100 个字符")
    private String keyword;

    @Size(max = 30, message = "学科不能超过 30 个字符")
    private String subject;

    private AnalysisStatus status;

    private Boolean mastered;

    @Min(value = 0, message = "页码不能小于 0")
    private int page = 0;

    @Min(value = 1, message = "每页至少返回 1 条记录")
    @Max(value = 100, message = "每页最多返回 100 条记录")
    private int size = 20;

    @Pattern(
            regexp = "createdAt,(asc|desc)",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "sort 只支持 createdAt,asc 或 createdAt,desc"
    )
    private String sort = "createdAt,desc";
}
