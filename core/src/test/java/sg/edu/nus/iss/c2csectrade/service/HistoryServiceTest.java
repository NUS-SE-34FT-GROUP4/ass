package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.dto.ViewHistoryDTO;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Views are kept in Redis: a sorted set of the last 100 products per user,
 * scored by time, and a hash of interest tags taken from each product's
 * category path and condition. User 7 views product 42.
 */
@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ZSetOperations<String, Object> zSet;
    @Mock
    private HashOperations<String, Object, Object> hash;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private HybridRecommendationService hybridRecommendationService;

    @InjectMocks
    private HistoryService historyService;

    @Test
    void aViewIsRecordedAndItsTagsAddedToTheProfile() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(redisTemplate.opsForHash()).thenReturn(hash);
        when(zSet.zCard("history:user:7")).thenReturn(1L);
        Product lamp = new Product();
        lamp.setCategory("Home > Lighting");
        lamp.setConditionLevel(9);
        when(productMapper.selectById(42L)).thenReturn(lamp);

        historyService.recordView(7L, 42L);

        verify(zSet).add(eq("history:user:7"), eq("42"), anyDouble());
        verify(zSet, never()).removeRange(anyString(), anyLong(), anyLong());
        for (String tag : List.of("Home > Lighting", "Home", "Lighting", "condition_9")) {
            verify(hash).increment("profile:user:7", tag, 1);
        }
        verify(hybridRecommendationService).updatePopularity(42L, "view");
    }

    @Test
    void theHistoryIsTrimmedToTheLast100() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(zSet.zCard("history:user:7")).thenReturn(103L);
        when(productMapper.selectById(42L)).thenReturn(null);

        historyService.recordView(7L, 42L);

        verify(zSet).removeRange("history:user:7", 0, 2);
    }

    @Test
    void aProductWithoutCategoryOrConditionAddsNoTags() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(productMapper.selectById(42L)).thenReturn(new Product());

        historyService.recordView(7L, 42L);

        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    void aProfileFailureDoesNotLoseTheView() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(productMapper.selectById(42L)).thenThrow(new RuntimeException("db down"));

        historyService.recordView(7L, 42L);

        verify(zSet).add(eq("history:user:7"), eq("42"), anyDouble());
        verify(hybridRecommendationService).updatePopularity(42L, "view");
    }

    @Test
    void anAnonymousViewIsIgnored() {
        historyService.recordView(null, 42L);
        historyService.recordView(7L, null);

        verifyNoInteractions(redisTemplate, productMapper, hybridRecommendationService);
    }

    @Test
    void theHistoryIsNewestFirst() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        Set<ZSetOperations.TypedTuple<Object>> items = new LinkedHashSet<>();
        items.add(new DefaultTypedTuple<>("43", 2000.0));
        items.add(new DefaultTypedTuple<>("42", 1000.0));
        when(zSet.reverseRangeWithScores("history:user:7", 0, 19)).thenReturn(items);

        List<ViewHistoryDTO> history = historyService.getUserHistory(7L, 20);

        assertThat(history).extracting(ViewHistoryDTO::getProductId).containsExactly(43L, 42L);
        assertThat(history.get(0).getTimestamp()).isEqualTo(2000L);
    }

    @Test
    void noHistoryIsAnEmptyList() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(zSet.reverseRangeWithScores("history:user:7", 0, 9)).thenReturn(Set.of());
        when(zSet.reverseRangeWithScores("history:user:8", 0, 9)).thenReturn(null);

        assertThat(historyService.getUserHistory(7L, 10)).isEmpty();
        assertThat(historyService.getUserHistory(8L, 10)).isEmpty();
    }

    @Test
    void theTopInterestsComeFirst() {
        when(redisTemplate.opsForHash()).thenReturn(hash);
        Map<Object, Object> interests = new LinkedHashMap<>();
        interests.put("Home", "2");
        interests.put("Lighting", "5");
        interests.put("condition_9", "1");
        when(hash.entries("profile:user:7")).thenReturn(interests);
        when(hash.entries("profile:user:8")).thenReturn(Map.of());

        Map<String, Integer> top = historyService.getUserInterestProfile(7L, 2);

        assertThat(top).containsExactly(Map.entry("Lighting", 5), Map.entry("Home", 2));
        assertThat(historyService.getUserInterestProfile(8L, 2)).isEmpty();
    }

    @Test
    void usersWithHistoryAreFoundByKey() {
        when(redisTemplate.keys("history:user:*")).thenReturn(Set.of("history:user:7", "history:user:9"));

        assertThat(historyService.getAllUsersWithHistory()).containsExactlyInAnyOrder("7", "9");
    }

    @Test
    void noUsersWithHistoryIsAnEmptySet() {
        when(redisTemplate.keys("history:user:*")).thenReturn(null);
        assertThat(historyService.getAllUsersWithHistory()).isEmpty();

        when(redisTemplate.keys("history:user:*")).thenReturn(Set.of());
        assertThat(historyService.getAllUsersWithHistory()).isEmpty();
    }

    @Test
    void viewedProductIdsAreParsed() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(zSet.range("history:user:7", 0, -1)).thenReturn(Set.of("42", "43"));
        when(zSet.range("history:user:8", 0, -1)).thenReturn(null);
        when(zSet.range("history:user:9", 0, -1)).thenReturn(Set.of());

        assertThat(historyService.getUserViewedProductIds(7L)).containsExactlyInAnyOrder(42L, 43L);
        assertThat(historyService.getUserViewedProductIds(8L)).isEmpty();
        assertThat(historyService.getUserViewedProductIds(9L)).isEmpty();
    }
}
