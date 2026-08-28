package com.learn.mistakeservice.service;

import com.learn.mistakeservice.vo.MistakeDetailVO;

import java.util.UUID;

public interface MistakeService {
    MistakeDetailVO getMistake(UUID id);
}
