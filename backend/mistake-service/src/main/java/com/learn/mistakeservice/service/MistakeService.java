package com.learn.mistakeservice.service;

import com.learn.mistakeservice.dto.CreateMistakeDTO;
import com.learn.mistakeservice.dto.MistakeListQueryDTO;
import com.learn.mistakeservice.dto.UpdateMasteryDTO;
import com.learn.common.vo.PageVO;
import com.learn.mistakeservice.vo.CreateMistakeVO;
import com.learn.mistakeservice.vo.MistakeDetailVO;
import com.learn.mistakeservice.vo.MistakeSummaryVO;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface MistakeService {
    PageVO<MistakeSummaryVO> listMistakes(MistakeListQueryDTO query);

    MistakeDetailVO getMistake(UUID id);

    CreateMistakeVO createMistake(CreateMistakeDTO request, MultipartFile image);

    MistakeSummaryVO updateMastery(UUID id, @Valid UpdateMasteryDTO updateMasteryDTO);
}
