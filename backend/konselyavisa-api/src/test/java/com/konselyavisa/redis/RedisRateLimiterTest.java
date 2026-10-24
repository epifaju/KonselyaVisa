package com.konselyavisa.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private KonselyaRedisProperties properties;
    private RedisRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new KonselyaRedisProperties();
        properties.setReadPerMinute(2);
        properties.setWritePerMinute(1);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        limiter = new RedisRateLimiter(redis, properties);
    }

    @Test
    void checkNoOpWhenConfiguredLimitBelowOne() {
        properties.setReadPerMinute(0);

        assertThatCode(() -> limiter.check("org:1", false)).doesNotThrowAnyException();
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    void checkSetsKeyExpiryOnFirstIncrement() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        limiter.check("org:1", true);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).increment(keyCaptor.capture());
        assertThatCode(() -> {}).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(keyCaptor.getValue()).contains(":w:org:1");
        verify(redis).expire(eq(keyCaptor.getValue()), eq(70L), eq(TimeUnit.SECONDS));
    }

    @Test
    void checkThrowsTooManyRequestsWhenCountExceedsLimit() {
        when(valueOps.increment(anyString())).thenReturn(3L);

        assertThatThrownBy(() -> limiter.check("ip:1.1.1.1", false))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.rate_limited");
        verify(redis, never()).expire(anyString(), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void checkDoesNotThrowWhenCountEqualsLimit() {
        when(valueOps.increment(anyString())).thenReturn(2L);

        assertThatCode(() -> limiter.check("org:1", false)).doesNotThrowAnyException();
    }

    @Test
    void checkUsesSeparateKeysForReadAndWrite() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        limiter.check("bucket", false);
        limiter.check("bucket", true);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(valueOps, org.mockito.Mockito.times(2)).increment(keys.capture());
        org.assertj.core.api.Assertions.assertThat(keys.getAllValues().get(0)).contains(":r:bucket");
        org.assertj.core.api.Assertions.assertThat(keys.getAllValues().get(1)).contains(":w:bucket");
    }
}
