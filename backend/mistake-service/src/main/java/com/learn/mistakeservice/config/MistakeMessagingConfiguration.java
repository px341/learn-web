package com.learn.mistakeservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 错题分析任务的 RabbitMQ 拓扑与消费策略配置。
 *
 * <p>主交换机和主队列承载待分析任务；监听器对临时异常执行无状态重试，并使用指数退避
 * 降低上游故障期间的请求压力。重试耗尽后，{@link RepublishMessageRecoverer} 将原消息连同
 * 异常相关 header 转发到独立失败交换机，由死信消费者把业务任务终结为失败状态。</p>
 *
 * <p>所有交换机和队列均为持久化声明。配置同时启用 Spring 调度，用于 Outbox 发布和
 * 分析租约恢复任务。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class MistakeMessagingConfiguration {

    /** 接收正常错题分析请求的持久化直连交换机。 */
    public static final String ANALYSIS_EXCHANGE = "mistake.analysis";

    /** 主分析消费者监听的持久化队列。 */
    public static final String ANALYSIS_QUEUE = "mistake.analysis.requested";

    /** 把分析请求从主交换机路由到主队列的 routing key。 */
    public static final String ANALYSIS_ROUTING_KEY = "mistake.analysis.requested";

    /** 接收重试耗尽消息的持久化直连交换机。 */
    public static final String ANALYSIS_FAILED_EXCHANGE = "mistake.analysis.failed";

    /** 失败任务消费者监听的持久化队列。 */
    public static final String ANALYSIS_FAILED_QUEUE = "mistake.analysis.failed";

    /** 把重试耗尽消息从失败交换机路由到失败队列的 routing key。 */
    public static final String ANALYSIS_FAILED_ROUTING_KEY = "mistake.analysis.failed";

    /**
     * 声明主分析交换机。
     *
     * @return 持久化、非自动删除的 Direct Exchange
     */
    @Bean
    public DirectExchange mistakeAnalysisExchange() {
        return new DirectExchange(ANALYSIS_EXCHANGE, true, false);
    }

    /**
     * 声明主分析队列。
     *
     * <p>队列不附加 RabbitMQ 原生死信参数，以兼容已经部署的同名队列；重试耗尽后的转发由
     * 应用层 {@link RepublishMessageRecoverer} 完成。</p>
     *
     * @return 持久化主队列
     */
    @Bean
    public Queue mistakeAnalysisQueue() {
        return QueueBuilder.durable(ANALYSIS_QUEUE).build();
    }

    /**
     * 使用主 routing key 绑定主队列和主交换机。
     *
     * @param mistakeAnalysisQueue 主分析队列
     * @param mistakeAnalysisExchange 主分析交换机
     * @return 主分析绑定关系
     */
    /**
     * 声明接收重试耗尽消息的失败交换机。
     *
     * @return 持久化、非自动删除的 Direct Exchange
     */
    @Bean
    public Binding mistakeAnalysisBinding(
            @Qualifier("mistakeAnalysisQueue") Queue mistakeAnalysisQueue,
            @Qualifier("mistakeAnalysisExchange") DirectExchange mistakeAnalysisExchange
    ) {
        return BindingBuilder.bind(mistakeAnalysisQueue)
                .to(mistakeAnalysisExchange)
                .with(ANALYSIS_ROUTING_KEY);
    }

    /**
     * 声明失败消息队列。
     *
     * @return 持久化失败队列
     */
    @Bean
    public DirectExchange mistakeAnalysisFailedExchange() {
        return new DirectExchange(ANALYSIS_FAILED_EXCHANGE, true, false);
    }

    @Bean
    public Queue mistakeAnalysisFailedQueue() {
        return QueueBuilder.durable(ANALYSIS_FAILED_QUEUE).build();
    }

    @Bean
    public Binding mistakeAnalysisFailedBinding(
            @Qualifier("mistakeAnalysisFailedQueue") Queue failedQueue,
            @Qualifier("mistakeAnalysisFailedExchange") DirectExchange failedExchange
    ) {
        return BindingBuilder.bind(failedQueue)
                .to(failedExchange)
                .with(ANALYSIS_FAILED_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter mistakeMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RetryOperationsInterceptor mistakeAnalysisRetryInterceptor(
            RabbitTemplate rabbitTemplate,
            @Value("${analysis.retry.max-attempts:3}") int maxAttempts,
            @Value("${analysis.retry.initial-interval:2s}") java.time.Duration initialInterval,
            @Value("${analysis.retry.multiplier:2}") double multiplier,
            @Value("${analysis.retry.max-interval:10s}") java.time.Duration maxInterval
    ) {
        RepublishMessageRecoverer recoverer = new RepublishMessageRecoverer(
                rabbitTemplate,
                ANALYSIS_FAILED_EXCHANGE,
                ANALYSIS_FAILED_ROUTING_KEY
        );
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(
                        initialInterval.toMillis(), multiplier, maxInterval.toMillis()
                )
                .recoverer(recoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory mistakeAnalysisRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Qualifier("mistakeAnalysisRetryInterceptor") RetryOperationsInterceptor retryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}
    /**
     * 使用失败 routing key 绑定失败队列和失败交换机。
     *
     * @param failedQueue 失败消息队列
     * @param failedExchange 失败消息交换机
     * @return 失败消息绑定关系
     */
    /**
     * 注册 RabbitMQ JSON 消息转换器。
     *
     * <p>复用应用统一的 {@link ObjectMapper}，确保消息 DTO 的 UUID 和其他字段与 HTTP JSON
     * 契约采用一致的序列化规则。</p>
     *
     * @param objectMapper 应用级 Jackson 配置
     * @return 用于生产和消费消息的 Jackson 转换器
     */
    /**
     * 创建主分析消费者使用的无状态重试拦截器。
     *
     * <p>每次尝试都会重新进入监听方法，业务层先把临时失败的任务退回 {@code QUEUED}，因此
     * 下一次尝试可以重新领取任务。达到最大尝试次数后不再抛回主队列，而是把原消息重新发布
     * 到失败交换机。</p>
     *
     * @param rabbitTemplate 用于把重试耗尽消息重新发布到失败交换机
     * @param maxAttempts 包含首次执行在内的最大尝试次数
     * @param initialInterval 第一次重试前的等待时间
     * @param multiplier 后续退避间隔的倍数
     * @param maxInterval 单次退避允许的最长时间
     * @return 可安装到监听容器 advice chain 的重试拦截器
     */
    /**
     * 创建主分析队列专用的监听容器工厂。
     *
     * <p>先由 Spring Boot configurer 应用连接、确认、并发及消息转换等公共 RabbitMQ 配置，
     * 再追加本服务的重试拦截器。失败队列消费者继续使用默认容器工厂，避免对已经重试耗尽的
     * 消息再次套用相同策略。</p>
     *
     * @param configurer Spring Boot 提供的监听容器公共配置器
     * @param connectionFactory RabbitMQ 连接工厂
     * @param retryInterceptor 主分析消费重试策略
     * @return 主分析监听器专用容器工厂
     */
