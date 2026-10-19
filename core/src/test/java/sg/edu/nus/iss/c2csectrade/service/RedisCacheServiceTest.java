package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Thin Redis helpers. The lock is the one the order-expiry job takes: it is
 * only released by the holder whose id it carries.
 */
@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> values;
    @Mock
    private ZSetOperations<String, Object> zSet;

    @InjectMocks
    private RedisCacheService cache;

    @Test
    void productsAndUsersAreCachedUnderTheirOwnPrefix() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("cache:product:42")).thenReturn("lamp");
        when(values.get("cache:user:7")).thenReturn("buyer7");

        cache.cacheProduct(42L, "lamp", 10);
        cache.cacheUser(7L, "buyer7", 5);

        verify(values).set("cache:product:42", "lamp", 10, TimeUnit.MINUTES);
        verify(values).set("cache:user:7", "buyer7", 5, TimeUnit.MINUTES);
        assertThat(cache.getCachedProduct(42L)).isEqualTo("lamp");
        assertThat(cache.getCachedUser(7L)).isEqualTo("buyer7");
    }

    @Test
    void aLockIsTakenOnlyIfFree() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("lock:job", "me", 50, TimeUnit.SECONDS)).thenReturn(true).thenReturn(false).thenReturn(null);

        assertThat(cache.acquireLock("job", "me", 50)).isTrue();
        assertThat(cache.acquireLock("job", "me", 50)).isFalse();
        assertThat(cache.acquireLock("job", "me", 50)).isFalse();
    }

    @Test
    void onlyTheHolderReleasesTheLock() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("lock:job")).thenReturn("me");
        when(redisTemplate.delete("lock:job")).thenReturn(true);

        assertThat(cache.releaseLock("job", "someone-else")).isFalse();
        verify(redisTemplate, never()).delete("lock:job");
        assertThat(cache.releaseLock("job", "me")).isTrue();
    }

    @Test
    void hotProductsAreRankedByScore() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        Set<Object> ranked = new LinkedHashSet<>(List.of("43", "42"));
        when(zSet.reverseRange("cache:hot:products", 0, 1)).thenReturn(ranked);
        when(zSet.reverseRange("cache:hot:products", 0, 4)).thenReturn(null);

        cache.updateHotProducts(42L, 3.5);

        verify(zSet).add("cache:hot:products", "42", 3.5);
        assertThat(cache.getHotProducts(2)).containsExactly(43L, 42L);
        assertThat(cache.getHotProducts(5)).isEmpty();
    }

    @Test
    void noHotProductsIsAnEmptyList() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(zSet.reverseRange("cache:hot:products", 0, 1)).thenReturn(Set.of());

        assertThat(cache.getHotProducts(2)).isEmpty();
    }

    @Test
    void keysAreReadWrittenCountedAndDeleted() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.multiGet(List.of("a", "b"))).thenReturn(List.of("1", "2"));
        when(values.increment("hits")).thenReturn(4L);
        when(redisTemplate.hasKey("a")).thenReturn(true);
        when(redisTemplate.hasKey("b")).thenReturn(null);
        when(redisTemplate.keys("cache:*")).thenReturn(Set.of("cache:x"));

        assertThat(cache.multiGet(List.of("a", "b"))).containsExactly("1", "2");
        assertThat(cache.increment("hits")).isEqualTo(4L);
        assertThat(cache.exists("a")).isTrue();
        assertThat(cache.exists("b")).isFalse();
        assertThat(cache.keys("cache:*")).containsExactly("cache:x");
        cache.delete("a");
        cache.deleteMultiple(List.of("a", "b"));
        cache.batchSet(Map.of("a", "1"), 5);

        verify(redisTemplate).delete("a");
        verify(redisTemplate).delete(List.of("a", "b"));
        verify(redisTemplate).executePipelined(any(org.springframework.data.redis.core.RedisCallback.class));
    }

    @Test
    void aCounterGetsItsExpiryOnTheFirstIncrementOnly() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment("rate:7")).thenReturn(1L).thenReturn(2L).thenReturn(null);

        assertThat(cache.incrementWithExpire("rate:7", 60)).isEqualTo(1L);
        assertThat(cache.incrementWithExpire("rate:7", 60)).isEqualTo(2L);
        assertThat(cache.incrementWithExpire("rate:7", 60)).isNull();

        verify(redisTemplate).expire("rate:7", 60, TimeUnit.SECONDS);
    }

    @Test
    void theBloomFilterSetsAndChecksThreeBits() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        // "a".hashCode() is 97, so the bits are 97, 97*31 and 97*37
        when(values.getBit("bloom", 97)).thenReturn(true);
        when(values.getBit("bloom", 3007)).thenReturn(true);
        when(values.getBit("bloom", 3589)).thenReturn(true).thenReturn(false);

        cache.bloomAdd("bloom", "a");

        verify(values).setBit("bloom", 97, true);
        verify(values).setBit("bloom", 3007, true);
        verify(values).setBit("bloom", 3589, true);
        assertThat(cache.bloomContains("bloom", "a")).isTrue();
        assertThat(cache.bloomContains("bloom", "a")).isFalse();
    }
}
