package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Experiments live in Redis for 30 days. A user keeps the variant they were
 * first given; with no experiment they are in the control group.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ABTestServiceTest {

    private static final String EXP = "recommendation_algorithm_v1";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> values;
    @Mock
    private ListOperations<String, Object> lists;
    @Mock
    private SetOperations<String, Object> sets;

    @InjectMocks
    private ABTestService abTest;

    @BeforeEach
    void redis() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.opsForList()).thenReturn(lists);
        when(redisTemplate.opsForSet()).thenReturn(sets);
    }

    @Test
    void theDefaultExperimentSplitsFiveWays() {
        abTest.initializeDefaultExperiment();

        ArgumentCaptor<Object> stored = ArgumentCaptor.forClass(Object.class);
        verify(values).set(eq("abtest:config:" + EXP), stored.capture(), eq(30L), eq(TimeUnit.DAYS));
        ABTestService.Experiment experiment = (ABTestService.Experiment) stored.getValue();
        assertThat(experiment.getExperimentId()).isEqualTo(EXP);
        assertThat(experiment.getName()).contains("Recommendation");
        assertThat(experiment.isActive()).isTrue();
        assertThat(experiment.getVariants()).hasSize(5).containsEntry("control", 0.2);
    }

    @Test
    void aUserKeepsTheirFirstVariant() {
        when(values.get("abtest:user:" + EXP + ":7")).thenReturn("variant_b");

        assertThat(abTest.assignUserToVariant(7L, EXP)).isEqualTo("variant_b");
        verify(values, never()).get("abtest:config:" + EXP);
    }

    @Test
    void withoutAnExperimentEveryoneIsControl() {
        assertThat(abTest.assignUserToVariant(7L, "unknown")).isEqualTo("control");
    }

    @Test
    void aNewUserIsAssignedAndRemembered() {
        when(values.get("abtest:config:exp")).thenReturn(experiment(Map.of("only", 1.0)));

        assertThat(abTest.assignUserToVariant(7L, "exp")).isEqualTo("only");
        verify(values).set("abtest:user:exp:7", "only", 30, TimeUnit.DAYS);
    }

    @Test
    void anUnderfilledSplitFallsBackToAVariant() {
        when(values.get("abtest:config:exp")).thenReturn(experiment(Map.of("tiny", 0.0)));

        assertThat(abTest.assignUserToVariant(7L, "exp")).isEqualTo("tiny");
    }

    @ParameterizedTest
    @CsvSource({"control, collaborative_filtering", "variant_a, content_based", "variant_b, hybrid",
            "variant_c, ncf_neural", "variant_d, realtime", "other, collaborative_filtering"})
    void eachVariantHasAStrategy(String variant, String strategy) {
        when(values.get("abtest:user:" + EXP + ":7")).thenReturn(variant);

        assertThat(abTest.getRecommendationStrategy(7L)).isEqualTo(strategy);
    }

    @Test
    void metricsAreCountedAndTheirValuesCapped() {
        when(lists.size("abtest:metrics:exp:control:clicks:values")).thenReturn(10_001L);

        abTest.trackMetric("exp", "control", "clicks", 1.0);

        verify(values).increment("abtest:metrics:exp:control:clicks");
        verify(lists).rightPush("abtest:metrics:exp:control:clicks:values", 1.0);
        verify(lists).trim("abtest:metrics:exp:control:clicks:values", -10000, -1);
    }

    @Test
    void clicksAndConversionsAreTrackedForTheUsersVariant() {
        when(values.get("abtest:user:exp:7")).thenReturn("control");

        abTest.trackClick(7L, "exp", 42L);
        abTest.trackConversion(7L, "exp", 42L, 25.0);

        verify(sets).add("abtest:metrics:exp:control:unique_users", "7");
        verify(lists).rightPush("abtest:metrics:exp:control:revenue:values", 25.0);
        verify(values).increment("abtest:metrics:exp:control:conversions");
        verify(lists, never()).trim(any(), any(Long.class), any(Long.class));
    }

    @Test
    void resultsGiveRatesPerVariant() {
        when(values.get("abtest:config:exp")).thenReturn(experiment(Map.of("control", 1.0)));
        when(values.get("abtest:metrics:exp:control:impressions")).thenReturn(200L);
        when(values.get("abtest:metrics:exp:control:clicks")).thenReturn(20L);
        when(values.get("abtest:metrics:exp:control:conversions")).thenReturn(5L);
        when(lists.range("abtest:metrics:exp:control:revenue:values", 0, -1)).thenReturn(List.of(10.0, 30.0, "bad"));

        Map<String, Object> control = abTest.getExperimentResults("exp").get("control");

        assertThat(control).containsEntry("ctr", 0.1).containsEntry("cvr", 0.25)
                .containsEntry("totalRevenue", 40.0).containsEntry("avgRevenue", 8.0);
    }

    @Test
    void aVariantWithNoTrafficHasZeroRates() {
        when(values.get("abtest:config:exp")).thenReturn(experiment(Map.of("control", 1.0)));

        Map<String, Object> control = abTest.getExperimentResults("exp").get("control");

        assertThat(control).containsEntry("impressions", 0L).containsEntry("ctr", 0.0)
                .containsEntry("cvr", 0.0).containsEntry("avgRevenue", 0.0);
        assertThat(abTest.getExperimentResults("unknown")).isEmpty();
    }

    @Test
    void aBigEnoughClickRateGapIsSignificant() {
        when(values.get("abtest:config:exp")).thenReturn(experiment(Map.of("a", 0.5, "b", 0.5)));
        when(values.get("abtest:metrics:exp:a:impressions")).thenReturn(1000L);
        when(values.get("abtest:metrics:exp:a:clicks")).thenReturn(100L);
        when(values.get("abtest:metrics:exp:b:impressions")).thenReturn(1000L);
        when(values.get("abtest:metrics:exp:b:clicks")).thenReturn(200L);

        assertThat(abTest.isStatisticallySignificant("exp", "a", "b")).isTrue();
        assertThat(abTest.isStatisticallySignificant("exp", "a", "missing")).isFalse();
    }

    @Test
    void tooFewImpressionsAreNeverSignificant() {
        when(values.get("abtest:config:exp")).thenReturn(experiment(Map.of("a", 0.5, "b", 0.5)));
        when(values.get("abtest:metrics:exp:a:impressions")).thenReturn(99L);
        when(values.get("abtest:metrics:exp:b:impressions")).thenReturn(1000L);

        assertThat(abTest.isStatisticallySignificant("exp", "a", "b")).isFalse();
    }

    private static ABTestService.Experiment experiment(Map<String, Double> variants) {
        ABTestService.Experiment experiment = new ABTestService.Experiment("exp", "Test");
        variants.forEach(experiment::addVariant);
        return experiment;
    }
}
