package com.learn.mistakeservice.controller;

import com.learn.common.vo.ApiResponse;
import com.learn.common.vo.PageVO;
import com.learn.mistakeservice.dto.CreateMistakeDTO;
import com.learn.mistakeservice.dto.MistakeListQueryDTO;
import com.learn.mistakeservice.dto.UpdateMasteryDTO;
import com.learn.mistakeservice.service.MistakeService;
import com.learn.mistakeservice.vo.CreateMistakeVO;
import com.learn.mistakeservice.vo.MistakeDetailVO;
import com.learn.mistakeservice.vo.MistakeSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping
    @Operation(summary = "分页查询当前用户的错题")
    public ApiResponse<PageVO<MistakeSummaryVO>> listMistakes(
            @Valid @ModelAttribute MistakeListQueryDTO query
    ) {
        return ApiResponse.success(mistakeService.listMistakes(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询错题详情")
    public ApiResponse<MistakeDetailVO> getMistake(@PathVariable UUID id) {
        return ApiResponse.success(mistakeService.getMistake(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "创建错题并异步开始分析")
    public ApiResponse<CreateMistakeVO> createMistake(
            @Valid @ModelAttribute CreateMistakeDTO request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ApiResponse.success(mistakeService.createMistake(request, image));
    }

    @PatchMapping("/{id}/mastery")
    @Operation(summary = "标记或取消标记“已掌握”")
    public ApiResponse<MistakeSummaryVO> updateMastery(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMasteryDTO updateMasteryDTO) {
        MistakeSummaryVO mistakeSummaryVO = mistakeService.updateMastery(id, updateMasteryDTO);
        return ApiResponse.success(mistakeSummaryVO);
    }
}
