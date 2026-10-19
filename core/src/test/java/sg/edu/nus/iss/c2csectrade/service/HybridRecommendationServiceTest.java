package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Similar products blend collaborative filtering (40%), content similarity
 * (40%) and popularity (20%). Popularity grows by action: view 1,
 * favourite 3, cart 5, review 8, order 10.
 */
@ExtendWith(MockitoExtension.class)
class HybridRecommendationServiceTest {

    private static final String POPULARITY = "recommend:popularity";

    @Mock
    private RecommendationEngineService collaborativeFiltering;
    @Mock
    private ContentBasedRecommendationService contentBased;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ZSetOperations<String, Object> zSet;

    @InjectMocks
    private HybridRecommendationService hybrid;

    @Test
    void bothSourcesAndPopularityAreBlended() {
        when(collaborativeFiltering.getSimilarProducts(42L, 4)).thenReturn(List.of(1L, 2L));
        when(contentBased.getContentBasedSimilar(42L, 4)).thenReturn(List.of(2L, 3L));
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(zSet.score(POPULARITY, "1")).thenReturn(null);
        when(zSet.score(POPULARITY, "2")).thenReturn(0.0);
        when(zSet.score(POPULARITY, "3")).thenReturn(5.0);

        // 1: 0.4 · 2: 0.2 + 0.4 = 0.6 · 3: 0.2 + 5 x 0.2 = 1.2
        assertThat(hybrid.getHybridSimilarProducts(42L, 2)).containsExactly(3L, 2L);
    }

    @Test
    void aRedisFailureOnlyDropsThePopularityBoost() {
        when(collaborativeFiltering.getSimilarProducts(42L, 2)).thenReturn(List.of(1L));
        when(contentBased.getContentBasedSimilar(42L, 2)).thenReturn(List.of());
        when(redisTemplate.opsForZSet()).thenThrow(new RuntimeException("redis down"));

        assertThat(hybrid.getHybridSimilarProducts(42L, 1)).containsExactly(1L);
    }

    @ParameterizedTest
    @CsvSource({"view, 1.0", "FAVORITE, 3.0", "cart, 5.0", "Order, 10.0", "review, 8.0", "share, 1.0"})
    void eachActionAddsItsWeight(String action, double weight) {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);

        hybrid.updatePopularity(42L, action);

        verify(zSet).incrementScore(POPULARITY, "42", weight);
    }

    @Test
    void aPopularityUpdateNeverFailsTheCaller() {
        when(redisTemplate.opsForZSet()).thenThrow(new RuntimeException("redis down"));

        hybrid.updatePopularity(42L, "view");
        hybrid.decayPopularityScores();
    }

    @Test
    void popularityDecaysByTenPercent() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(zSet.range(POPULARITY, 0, -1)).thenReturn(new LinkedHashSet<>(List.of("1", "2")));
        when(zSet.score(POPULARITY, "1")).thenReturn(10.0);
        when(zSet.score(POPULARITY, "2")).thenReturn(0.0);

        hybrid.decayPopularityScores();

        verify(zSet).add(POPULARITY, "1", 9.0);
        verify(zSet, never()).add(eq(POPULARITY), eq("2"), anyDouble());
    }

    @Test
    void nothingToDecayIsANoOp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        when(zSet.range(POPULARITY, 0, -1)).thenReturn(null).thenReturn(Set.of());

        hybrid.decayPopularityScores();
        hybrid.decayPopularityScores();

        verify(zSet, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void personalisedScoresAreNotImplementedYet() {
        assertThat(hybrid.getPersonalizedScores(Map.of("Home", 3), Set.of(42L), 5)).isEmpty();
    }
}
