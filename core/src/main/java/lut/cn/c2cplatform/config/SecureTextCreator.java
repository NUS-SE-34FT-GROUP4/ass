package lut.cn.c2cplatform.config;

import com.google.code.kaptcha.text.TextProducer;
import com.google.code.kaptcha.util.Configurable;

import java.security.SecureRandom;

/**
 * Captcha text from SecureRandom. Kaptcha's DefaultTextCreator uses
 * java.util.Random, whose output can be predicted (CVE-2018-18531).
 */
public class SecureTextCreator extends Configurable implements TextProducer {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String getText() {
        int length = getConfig().getTextProducerCharLength();
        char[] chars = getConfig().getTextProducerCharString();
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(chars[RANDOM.nextInt(chars.length)]);
        }
        return text.toString();
    }
}
