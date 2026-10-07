package lut.cn.c2cplatform.config;

import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lets the Elasticsearch 8 Java client talk to Amazon OpenSearch Service.
 * The client sends "compatible-with=8" media types, which OpenSearch rejects
 * with 406, and refuses responses without an X-Elastic-Product header, which
 * OpenSearch does not send. Enabled on AWS only (app.search.opensearch-compatibility);
 * the Search Service should move to the OpenSearch client instead (WP7).
 */
@Configuration
@ConditionalOnProperty(name = "app.search.opensearch-compatibility", havingValue = "true")
public class OpenSearchCompatibilityConfig {

    private static final String JSON = "application/json";

    @Bean
    public RestClientBuilderCustomizer openSearchCompatibility() {
        return new RestClientBuilderCustomizer() {
            @Override
            public void customize(org.elasticsearch.client.RestClientBuilder builder) {
            }

            @Override
            public void customize(org.apache.http.impl.nio.client.HttpAsyncClientBuilder builder) {
                builder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                    if (request.containsHeader("Content-Type")) {
                        request.setHeader("Content-Type", JSON);
                    }
                    request.setHeader("Accept", JSON);
                });
                builder.addInterceptorLast((HttpResponseInterceptor) (response, context) -> {
                    if (!response.containsHeader("X-Elastic-Product")) {
                        response.addHeader("X-Elastic-Product", "Elasticsearch");
                    }
                });
            }
        };
    }
}
