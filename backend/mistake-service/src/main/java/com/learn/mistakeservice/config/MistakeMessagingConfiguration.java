package com.learn.mistakeservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 错题分析任务的持久化 RabbitMQ 拓扑。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class MistakeMessagingConfiguration {

    public static final String ANALYSIS_EXCHANGE = "mistake.analysis";
    public static final String ANALYSIS_QUEUE = "mistake.analysis.requested";
    public static final String ANALYSIS_ROUTING_KEY = "mistake.analysis.requested";

    @Bean
    public DirectExchange mistakeAnalysisExchange() {
        return new DirectExchange(ANALYSIS_EXCHANGE, true, false);
    }

    @Bean
    public Queue mistakeAnalysisQueue() {
        return new Queue(ANALYSIS_QUEUE, true);
    }

    @Bean
    public Binding mistakeAnalysisBinding(
            Queue mistakeAnalysisQueue,
            DirectExchange mistakeAnalysisExchange
    ) {
        return BindingBuilder.bind(mistakeAnalysisQueue)
                .to(mistakeAnalysisExchange)
                .with(ANALYSIS_ROUTING_KEY);
    }
}
