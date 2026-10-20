package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Each active listing is compared with every other by TF-IDF over its
 * category, name, description, price band, condition and location, nudged
 * by price and condition closeness. The top 15 are kept in Redis for 7 days.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentBasedRecommendationServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ZSetOperations<String, Object> zSet;
    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ContentBasedRecommendationService contentBased;

    @Test
    void aSimilarListingRanksAboveAnUnrelatedOne() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        Product lamp = product(1L, "Desk lamp", "Home > Lighting", "LED desk lamp, warm light", "15", 9, "UTown");
        Product otherLamp = product(2L, "Desk lamp white", "Home > Lighting", "LED lamp for a desk", "18", 8, "UTown");
        Product textbook = product(3L, "Calculus textbook", "Books", "Second edition, some notes", "40", 6, "PGP");
        when(productMapper.selectAllActive()).thenReturn(List.of(lamp, otherLamp, textbook));

        contentBased.computeContentBasedSimilarity();

        ArgumentCaptor<Double> lampToLamp = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> lampToBook = ArgumentCaptor.forClass(Double.class);
        verify(zSet).add(eq("recommend:content:1"), eq("2"), lampToLamp.capture());
        verify(zSet).add(eq("recommend:content:1"), eq("3"), lampToBook.capture());
        assertThat(lampToLamp.getValue()).isGreaterThan(lampToBook.getValue());
        verify(redisTemplate).delete("recommend:content:1");
        verify(redisTemplate).expire("recommend:content:1", 7, TimeUnit.DAYS);
    }

    @Test
    void everyPriceBandAndSparseListingIsHandled() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        List<Product> products = new ArrayList<>();
        String[] prices = {null, "0", "10", "30", "70", "150", "300", "800"};
        for (int i = 0; i < prices.length; i++) {
            products.add(product((long) i + 1, "二手台灯 " + i, i % 2 == 0 ? " " : "Home",
                    i % 3 == 0 ? null : "a used lamp", prices[i], i % 2 == 0 ? null : i, i % 2 == 0 ? null : "UTown"));
        }
        products.add(new Product());
        products.get(prices.length).setId(99L);
        when(productMapper.selectAllActive()).thenReturn(products);

        contentBased.computeContentBasedSimilarity();

        verify(redisTemplate).expire("recommend:content:99", 7, TimeUnit.DAYS);
        verify(zSet, atMost(15 * products.size())).add(eq("recommend:content:1"), eq("2"), anyDouble());
    }

    @Test
    void noListingsMeansNothingIsStored() {
        when(productMapper.selectAllActive()).thenReturn(List.of());

        contentBased.computeContentBasedSimilarity();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void aFailureIsLoggedNotThrown() {
        when(productMapper.selectAllActive()).thenThrow(new RuntimeException("db down"));

        contentBased.computeContentBasedSimilarity();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void similarListingsAreReadBackMostSimilarFirst() {
        when(redisTemplate.opsForZSet()).thenReturn(zSet);
        Set<Object> ranked = new LinkedHashSet<>(List.of("2", "3"));
        when(zSet.reverseRange("recommend:content:1", 0, 1)).thenReturn(ranked);
        when(zSet.reverseRange("recommend:content:5", 0, 1)).thenReturn(null);
        when(zSet.reverseRange("recommend:content:6", 0, 1)).thenReturn(Set.of());

        assertThat(contentBased.getContentBasedSimilar(1L, 2)).containsExactly(2L, 3L);
        assertThat(contentBased.getContentBasedSimilar(5L, 2)).isEmpty();
        assertThat(contentBased.getContentBasedSimilar(6L, 2)).isEmpty();
    }

    private static Product product(Long id, String name, String category, String description, String price,
            Integer condition, String location) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setCategory(category);
        product.setDescription(description);
        product.setPrice(price == null ? null : new BigDecimal(price));
        product.setConditionLevel(condition);
        product.setLocation(location);
        return product;
    }
}
