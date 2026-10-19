package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.service.impl.InMemoryCaptchaService;
import sg.edu.nus.iss.c2csectrade.service.impl.RedisCaptchaService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A captcha matches case-insensitively and can be used once; a wrong
 * guess does not use it up. Both stores behave the same way.
 */
class CaptchaServiceTest {

    @Test
    void anInMemoryCaptchaIsUsedOnce() {
        InMemoryCaptchaService captchas = new InMemoryCaptchaService();
        captchas.save("c1", "Ab12", 300);

        assertThat(captchas.verifyAndConsume("c1", "wrong")).isFalse();
        assertThat(captchas.verifyAndConsume("c1", "aB12")).isTrue();
        assertThat(captchas.verifyAndConsume("c1", "aB12")).isFalse();
    }

    @Test
    void anExpiredOrUnknownInMemoryCaptchaFails() {
        InMemoryCaptchaService captchas = new InMemoryCaptchaService();
        captchas.save("old", "ab12", -1);
        captchas.save("blank", null, 300);

        assertThat(captchas.verifyAndConsume("old", "ab12")).isFalse();
        assertThat(captchas.verifyAndConsume("missing", "ab12")).isFalse();
        assertThat(captchas.verifyAndConsume("blank", "ab12")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRedisCaptchaExpiresWithItsTtlAndIsUsedOnce() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("captcha:c1")).thenReturn("Ab12");
        RedisCaptchaService captchas = new RedisCaptchaService(redis);

        captchas.save("c1", "Ab12", 300);
        verify(values).set("captcha:c1", "Ab12", 300, TimeUnit.SECONDS);

        assertThat(captchas.verifyAndConsume("c1", "wrong")).isFalse();
        verify(redis, never()).delete("captcha:c1");
        assertThat(captchas.verifyAndConsume("c1", "aB12")).isTrue();
        verify(redis).delete("captcha:c1");
    }

    @Test
    void aRedisCaptchaWithoutIdOrCodeIsIgnored() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisCaptchaService captchas = new RedisCaptchaService(redis);

        captchas.save(null, "ab12", 300);
        captchas.save("c1", null, 300);
        assertThat(captchas.verifyAndConsume(null, "ab12")).isFalse();
        assertThat(captchas.verifyAndConsume("c1", null)).isFalse();
        verifyNoInteractions(redis);
    }
}
