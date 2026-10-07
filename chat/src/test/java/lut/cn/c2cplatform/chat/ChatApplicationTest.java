package lut.cn.c2cplatform.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The load balancer keeps a task only while /actuator/health answers 200,
 * so the service must start and expose it without any other configuration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatApplicationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void healthEndpointIsUp() {
        assertThat(rest.getForObject("/actuator/health", String.class)).contains("\"status\":\"UP\"");
    }
}
