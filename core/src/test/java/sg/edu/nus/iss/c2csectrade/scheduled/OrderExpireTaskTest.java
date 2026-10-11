package sg.edu.nus.iss.c2csectrade.scheduled;

import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.service.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpireTaskTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RedisCacheService redisCacheService;

    @InjectMocks
    private OrderExpireTask task;

    @Test
    void lockHolderExpiresOrdersAndReleasesTheLock() {
        when(redisCacheService.acquireLock(eq(OrderExpireTask.LOCK_RESOURCE), anyString(), eq(OrderExpireTask.LOCK_SECONDS)))
                .thenReturn(true);

        task.closeExpiredOrders();

        verify(orderMapper).expirePendingOrders();
        ArgumentCaptor<String> acquiredWith = ArgumentCaptor.forClass(String.class);
        verify(redisCacheService).acquireLock(eq(OrderExpireTask.LOCK_RESOURCE), acquiredWith.capture(), anyLong());
        verify(redisCacheService).releaseLock(OrderExpireTask.LOCK_RESOURCE, acquiredWith.getValue());
    }

    @Test
    void skipsTheRunWhileAnotherInstanceHoldsTheLock() {
        when(redisCacheService.acquireLock(anyString(), anyString(), anyLong())).thenReturn(false);

        task.closeExpiredOrders();

        verify(orderMapper, never()).expirePendingOrders();
        verify(redisCacheService, never()).releaseLock(anyString(), anyString());
    }

    @Test
    void stillExpiresOrdersWhenRedisIsDown() {
        when(redisCacheService.acquireLock(anyString(), anyString(), anyLong()))
                .thenThrow(new RedisConnectionFailureException("down"));

        task.closeExpiredOrders();

        verify(orderMapper).expirePendingOrders();
    }

    @Test
    void releasesTheLockEvenWhenTheUpdateFails() {
        when(redisCacheService.acquireLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(orderMapper.expirePendingOrders()).thenThrow(new RuntimeException("db down"));

        task.closeExpiredOrders();

        verify(redisCacheService).releaseLock(eq(OrderExpireTask.LOCK_RESOURCE), anyString());
    }
}
