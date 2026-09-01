package com.learn.paymentservice.infrastructure;

import com.learn.paymentservice.exception.PaymentLockUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLockExecutorTests {

    @Test
    void executesActionAfterAtomicLockAcquisition() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                org.mockito.ArgumentMatchers.eq("payment:mock:test"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30))
        )).thenReturn(true);
        RedisLockExecutor executor = new RedisLockExecutor(
                redisTemplate,
                Duration.ZERO,
                Duration.ofSeconds(30),
                Duration.ofMillis(1)
        );

        String result = executor.execute("payment:mock:test", () -> "done");

        assertThat(result).isEqualTo("done");
        verify(valueOperations).setIfAbsent(
                org.mockito.ArgumentMatchers.eq("payment:mock:test"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30))
        );
    }

    @Test
    void rejectsRequestWhenLockCannotBeAcquiredWithinWaitTime() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        )).thenReturn(false);
        RedisLockExecutor executor = new RedisLockExecutor(
                redisTemplate,
                Duration.ZERO,
                Duration.ofSeconds(30),
                Duration.ofMillis(1)
        );

        assertThatThrownBy(() -> executor.execute("payment:mock:test", () -> "unused"))
                .isInstanceOf(PaymentLockUnavailableException.class);
    }
}
