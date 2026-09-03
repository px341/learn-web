package com.learn.mistakeservice.service;

import java.util.UUID;

/** 消费异步任务后执行一次错题分析的业务入口。 */
public interface MistakeAnalysisService {

    void analyze(UUID mistakeId);

    void failAfterRetries(UUID mistakeId, String failureMessage);
}
