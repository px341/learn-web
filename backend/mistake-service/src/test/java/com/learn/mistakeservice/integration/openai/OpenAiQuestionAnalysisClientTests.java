package com.learn.mistakeservice.integration.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.mistakeservice.config.OpenAiAnalysisProperties;
import com.learn.mistakeservice.entity.PersonalQuestionEntity;
import com.learn.mistakeservice.exception.AnalysisPermanentException;
import com.learn.mistakeservice.exception.AnalysisTransientException;
import com.learn.mistakeservice.vo.MistakeAnalysisVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiQuestionAnalysisClientTests {

    @Test
    void sendsResponsesRequestAndParsesStructuredOutput() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiQuestionAnalysisClient client = client(builder, "test-key");
        ObjectMapper objectMapper = new ObjectMapper();
        String analysisJson = objectMapper.writeValueAsString(Map.of(
                "summary", "概念混淆",
                "knowledge", List.of("二次函数"),
                "steps", List.of("化为顶点式"),
                "suggestion", "复习顶点式",
                "answer", "最小值为 k",
                "confidence", 93
        ));
        String responseJson = objectMapper.writeValueAsString(Map.of(
                "status", "completed",
                "output", List.of(Map.of(
                        "type", "message",
                        "content", List.of(Map.of(
                                "type", "output_text",
                                "text", analysisJson
                        ))
                ))
        ));
        server.expect(once(), requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        MistakeAnalysisVO result = client.analyze(question(), null);

        assertThat(result.summary()).isEqualTo("概念混淆");
        assertThat(result.confidence()).isEqualTo(93);
        server.verify();
    }

    @Test
    void treatsRateLimitAsRetryable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiQuestionAnalysisClient client = client(builder, "test-key");
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.analyze(question(), null))
                .isInstanceOf(AnalysisTransientException.class);
    }

    @Test
    void refusesToCallApiWithoutEnvironmentKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiQuestionAnalysisClient client = client(builder, "");

        assertThatThrownBy(() -> client.analyze(question(), null))
                .isInstanceOf(AnalysisPermanentException.class)
                .hasMessage("分析服务尚未配置");
        server.verify();
    }

    private OpenAiQuestionAnalysisClient client(RestClient.Builder builder, String apiKey) {
        return new OpenAiQuestionAnalysisClient(
                builder.baseUrl("https://api.openai.com/v1").build(),
                new ObjectMapper(),
                new OpenAiAnalysisProperties(
                        URI.create("https://api.openai.com/v1"),
                        apiKey,
                        "gpt-5.4",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        1200
                )
        );
    }

    private PersonalQuestionEntity question() {
        PersonalQuestionEntity question = new PersonalQuestionEntity();
        question.setTitle("二次函数图像与最值");
        question.setSubject("数学");
        question.setChapter("函数");
        question.setQuestionType("概念不清");
        question.setStemText("已知二次函数……");
        question.setUserAnswer("x = 2");
        return question;
    }
}
