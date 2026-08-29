package com.learn.mistakeservice.controller;

import com.learn.mistakeservice.dto.CreateMistakeDTO;
import com.learn.mistakeservice.dto.UpdateMasteryDTO;
import com.learn.mistakeservice.exception.MistakeExceptionHandler;
import com.learn.mistakeservice.model.AnalysisStatus;
import com.learn.mistakeservice.service.MistakeService;
import com.learn.mistakeservice.vo.CreateMistakeVO;
import com.learn.mistakeservice.vo.MistakeDetailVO;
import com.learn.mistakeservice.vo.MistakeSummaryVO;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MistakeControllerTests {

    @Test
    void bindsFlatMultipartFieldsAndReturnsAccepted() throws Exception {
        UUID mistakeId = UUID.randomUUID();
        MistakeService mistakeService = new MistakeService() {
            @Override
            public MistakeDetailVO getMistake(UUID id) {
                return null;
            }

            @Override
            public CreateMistakeVO createMistake(CreateMistakeDTO request, MultipartFile image) {
                return new CreateMistakeVO(
                        new MistakeSummaryVO(
                                mistakeId,
                                request.title(),
                                request.subject(),
                                request.chapter(),
                                request.type(),
                                AnalysisStatus.QUEUED,
                                false,
                                Instant.parse("2026-08-22T01:42:00Z")
                        ),
                        2
                );
            }

            @Override
            public MistakeSummaryVO updateMastery(UUID id, UpdateMasteryDTO updateMasteryDTO) {
                return null;
            }
        };
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new MistakeController(mistakeService)
                )
                .setControllerAdvice(new MistakeExceptionHandler())
                .build();

        mockMvc.perform(multipart("/api/mistakes")
                        .param("title", "二次函数图像与最值")
                        .param("subject", "数学")
                        .param("chapter", "函数")
                        .param("type", "概念不清")
                        .param("text", "已知二次函数……")
                        .param("userAnswer", "x = 2"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.mistake.id").value(mistakeId.toString()))
                .andExpect(jsonPath("$.data.mistake.status").value("queued"))
                .andExpect(jsonPath("$.data.creditsRemaining").value(2));
    }
}
