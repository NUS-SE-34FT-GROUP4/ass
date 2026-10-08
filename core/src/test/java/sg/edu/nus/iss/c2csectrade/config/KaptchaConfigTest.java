package sg.edu.nus.iss.c2csectrade.config;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KaptchaConfigTest {

    private final DefaultKaptcha kaptcha = new KaptchaConfig().defaultKaptcha();

    @Test
    void textComesFromTheSecureProducer() {
        assertThat(kaptcha.getConfig().getTextProducerImpl()).isInstanceOf(SecureTextCreator.class);
    }

    @Test
    void textHasTheConfiguredLengthAndAlphabet() {
        String alphabet = new String(kaptcha.getConfig().getTextProducerCharString());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String text = kaptcha.createText();
            assertThat(text).hasSize(4);
            assertThat(text.chars()).allMatch(c -> alphabet.indexOf(c) >= 0);
            seen.add(text);
        }
        assertThat(seen).hasSizeGreaterThan(40);
    }
}
