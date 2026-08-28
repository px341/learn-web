package com.learn.mistakeservice.service;

import com.learn.mistakeservice.dto.CreateMistakeDTO;
import com.learn.mistakeservice.vo.CreateMistakeVO;
import com.learn.mistakeservice.vo.MistakeDetailVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface MistakeService {
    MistakeDetailVO getMistake(UUID id);

    CreateMistakeVO createMistake(CreateMistakeDTO request, MultipartFile image);
}
