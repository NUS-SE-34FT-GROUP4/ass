package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.service.CaptchaService;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A captcha is stored under a fresh id that the client gets back in the
 * Captcha-ID header, next to an uncacheable JPEG.
 */
@ExtendWith(MockitoExtension.class)
class CaptchaControllerTest {

    @Mock
    private DefaultKaptcha defaultKaptcha;
    @Mock
    private CaptchaService captchaService;

    @InjectMocks
    private CaptchaController captchaController;

    @Test
    void theCaptchaIsStoredUnderTheIdInTheHeader() {
        when(defaultKaptcha.createText()).thenReturn("ab12");
        when(defaultKaptcha.createImage("ab12")).thenReturn(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB));
        MockHttpServletResponse response = new MockHttpServletResponse();

        captchaController.generateCaptcha(response);

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        verify(captchaService).save(id.capture(), eq("ab12"), anyLong());
        assertThat(response.getHeader("Captcha-ID")).isEqualTo(id.getValue());
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getContentAsByteArray()).isNotEmpty();
    }
}
