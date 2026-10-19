package com.jinyan.assistant.service;

import com.jinyan.assistant.service.impl.RedisOperationsServiceImpl;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class RedisOperationsServiceTest {

    @Test
    void shouldCacheStringWithTtl() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket(anyString(), any())).thenReturn(bucket);
        RedisOperationsService service = new RedisOperationsServiceImpl(client);

        service.cacheUserProfile("u-1", "vip", Duration.ofMinutes(5));

        verify(bucket).set("vip", 300_000, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    void shouldReleaseLockOnlyWhenCurrentThreadOwnsIt() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedisOperationsService service = new RedisOperationsServiceImpl(client);

        String result = service.executeWithLock(
                "order-1", Duration.ofSeconds(1), Duration.ofSeconds(10), () -> "done");

        assertThat(result).isEqualTo("done");
        verify(lock).unlock();
    }

    @Test
    void shouldFailWithoutRunningCallbackWhenLockIsUnavailable() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);
        RedisOperationsService service = new RedisOperationsServiceImpl(client);

        assertThatThrownBy(() -> service.executeWithLock(
                "order-1", Duration.ofSeconds(1), Duration.ofSeconds(10), () -> {
                    throw new AssertionError("callback must not run");
                }))
                .isInstanceOf(IllegalStateException.class);
        verify(lock, never()).unlock();
    }
}
