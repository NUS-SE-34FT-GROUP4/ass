package sg.edu.nus.iss.c2csectrade.search.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URISyntaxException;

/**
 * Client for the product index. Amazon OpenSearch Service in the VPC accepts
 * plain HTTPS (its access policy allows the VPC), so no request signing.
 */
@Configuration
public class OpenSearchConfig {

    @Bean
    public OpenSearchTransport openSearchTransport(@Value("${app.opensearch.url}") String url)
            throws URISyntaxException {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return ApacheHttpClient5TransportBuilder.builder(HttpHost.create(url))
                .setMapper(new JacksonJsonpMapper(mapper))
                .build();
    }

    @Bean
    public OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }
}
