package com.learn.mistakeservice.service;

import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.vo.MistakeAnalysisVO;

/**
 * 外部题目分析能力的供应商无关接口。
 *
 * <p>业务编排层只依赖该契约，不直接依赖 OpenAI 的 HTTP 请求结构。实现类负责把题目和图片
 * 转换为供应商请求、验证外部响应，并使用分析异常类型表达失败是否值得由消息系统重试。</p>
 */
public interface QuestionAnalysisClient {

    /**
     * 分析一道题目并返回结构化结果。
     *
     * @param question 从数据库读取的可信题目及用户答案快照
     * @param imageContent 可选原图字节；纯文字题传 {@code null}
     * @return 字段完整、可持久化的结构化分析结果
     * @throws com.learn.mistakeservice.exception.AnalysisPermanentException 请求或响应无法通过重试修复时
     * @throws com.learn.mistakeservice.exception.AnalysisTransientException 网络、限流或上游临时故障时
     */
    MistakeAnalysisVO analyze(PersonalQuestionEntity question, byte[] imageContent);
}
