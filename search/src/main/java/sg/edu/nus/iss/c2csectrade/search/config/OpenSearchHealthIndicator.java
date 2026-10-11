package sg.edu.nus.iss.c2csectrade.search.config;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

/**
 * Shows the index cluster under /actuator/health as "opensearch". Spring Boot
 * has no indicator for the OpenSearch client. Red is DOWN; yellow is UP,
 * since a single node never places replicas.
 */
@Component("opensearch")
@ConditionalOnEnabledHealthIndicator("opensearch")
public class OpenSearchHealthIndicator extends AbstractHealthIndicator {

    private final OpenSearchClient client;

    public OpenSearchHealthIndicator(OpenSearchClient client) {
        this.client = client;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        HealthResponse health = client.cluster().health();
        String status = health.status().jsonValue();
        (status.equals("red") ? builder.down() : builder.up())
                .withDetail("cluster", health.clusterName())
                .withDetail("status", status);
    }
}
