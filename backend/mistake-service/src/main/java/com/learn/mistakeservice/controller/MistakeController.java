package com.learn.mistakeservice.controller;

import com.learn.common.vo.ApiResponse;
import com.learn.mistakeservice.service.MistakeService;
import com.learn.mistakeservice.vo.MistakeDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 错题部分
 *
 * <p> controller用于错题相关的API </p>
 */
@Tag(name = "错题", description = "当前用户的错题查询与管理")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/mistakes")
public class MistakeController {

    private final MistakeService mistakeService;

    @GetMapping("/{id}")
    @Operation(summary = "查询错题详情")
    public ApiResponse<MistakeDetailVO> getMistake(@PathVariable UUID id) {
        return ApiResponse.success(mistakeService.getMistake(id));
    }
}
