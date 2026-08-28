package com.learn.mistakeservice.controller;

import com.learn.mistakeservice.service.DashboardService;
import com.learn.mistakeservice.vo.DashboardStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.learn.common.vo.ApiResponse;

/**
 * 面板内容
 *
 * <p> 这个controller只写面板的内容 </p>
 */
@Tag(name = "面板", description = "用户错题信息")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @Operation(summary = "查询 Dashboard 错题统计")
    public ApiResponse<DashboardStatsVO> getDashboard() {
        DashboardStatsVO vo = dashboardService.getDashboard();
        return ApiResponse.success(vo);
    }
}
