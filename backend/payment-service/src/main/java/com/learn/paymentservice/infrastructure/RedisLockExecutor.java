package com.learn.paymentservice.infrastructure;

import com.learn.paymentservice.exception.PaymentLockUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 基于 Redis SET NX PX 的轻量分布式锁执行器。
 * 解锁时使用 Lua 校验持有者令牌，避免误删已经过期并被其他实例取得的锁。
 */
@Slf4j
@Component
public class RedisLockExecutor {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final Duration waitTime;
    private final Duration leaseTime;
    private final Duration retryInterval;

    public RedisLockExecutor(
            StringRedisTemplate redisTemplate,
            @Value("${payment.mock.lock.wait-time:3s}") Duration waitTime,
            @Value("${payment.mock.lock.lease-time:30s}") Duration leaseTime,
            @Value("${payment.mock.lock.retry-interval:50ms}") Duration retryInterval
    ) {
        this.redisTemplate = redisTemplate;
        this.waitTime = waitTime;
        this.leaseTime = leaseTime;
        this.retryInterval = retryInterval;
    }

    public <T> T execute(String key, Supplier<T> action) {
        String token = UUID.randomUUID().toString();
        acquire(key, token);
        try {
            return action.get();
        } finally {
            release(key, token);
        }
    }

    public void execute(String key, Runnable action) {
        execute(key, () -> {
            action.run();
            return null;
        });
    }

    private void acquire(String key, String token) {
        long deadline = System.nanoTime() + waitTime.toNanos();
        do {
            try {
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(key, token, leaseTime);
                if (Boolean.TRUE.equals(acquired)) {
                    return;
                }
            } catch (RuntimeException exception) {
                throw new PaymentLockUnavailableException(exception);
            }

            if (System.nanoTime() >= deadline) {
                throw new PaymentLockUnavailableException();
            }
            pauseBeforeRetry();
        } while (true);
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(retryInterval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentLockUnavailableException(exception);
        }
    }

    private void release(String key, String token) {
        try {
            redisTemplate.execute(
                    UNLOCK_SCRIPT,
                    Collections.singletonList(key),
                    token
            );
        } catch (RuntimeException exception) {
            // 业务事务此时可能已经提交，解锁失败不能把成功结果伪装成业务失败；锁会按租期过期。
            log.warn("释放模拟支付 Redis 锁失败, key={}", key, exception);
        }
    }
}
