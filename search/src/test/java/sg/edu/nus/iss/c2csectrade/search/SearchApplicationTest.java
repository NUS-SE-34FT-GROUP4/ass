package sg.edu.nus.iss.c2csectrade.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The load balancer keeps a task only while /actuator/health/liveness answers
 * 200, so the service must start and expose it without any other configuration.
 * There is no broker or OpenSearch here: their indicators are off and the
 * listener does not connect.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.health.rabbit.enabled=false",
        "management.health.opensearch.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class SearchApplicationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void healthEndpointIsUp() {
        assertThat(rest.getForObject("/actuator/health", String.class)).contains("\"status\":\"UP\"");
    }

    @Test
    void livenessEndpointIsUp() {
        assertThat(rest.getForObject("/actuator/health/liveness", String.class)).contains("\"status\":\"UP\"");
    }
}
