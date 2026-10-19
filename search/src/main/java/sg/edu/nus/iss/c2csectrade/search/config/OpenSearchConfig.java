package sg.edu.nus.iss.c2csectrade.search.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Options;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.net.ssl.SSLContext;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * Client for the product index. Amazon OpenSearch Service in the VPC accepts
 * plain HTTPS (its access policy allows the VPC), so no request signing.
 *
 * The client library allows 10 connections to OpenSearch and waits 30 seconds
 * for an answer. Under load, searches queued for those 10 connections long
 * after Core had given up on them (app.search.timeout, 2s) and answered from
 * MySQL. The pool is therefore sized here, and searches get their own short
 * timeouts so that they fail with 503 before Core stops waiting; indexing
 * keeps the long ones, since a bulk request of a rebuild may take a while.
 */
@Configuration
public class OpenSearchConfig {

    private static final Timeout CONNECT_TIMEOUT =
            Timeout.ofMilliseconds(ApacheHttpClient5TransportBuilder.DEFAULT_CONNECT_TIMEOUT_MILLIS);
    private static final Timeout SOCKET_TIMEOUT =
            Timeout.ofMilliseconds(ApacheHttpClient5TransportBuilder.DEFAULT_RESPONSE_TIMEOUT_MILLIS);

    @Bean
    public OpenSearchTransport openSearchTransport(
            @Value("${app.opensearch.url}") String url,
            @Value("${app.opensearch.max-connections}") int maxConnections,
            @Value("${app.opensearch.max-connections-per-route}") int maxConnectionsPerRoute)
            throws URISyntaxException, NoSuchAlgorithmException {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Replaces the builder's own connection manager, so its TLS and
        // timeout settings are repeated here
        var connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxConnections)
                .setMaxConnPerRoute(maxConnectionsPerRoute)
                .setTlsStrategy(ClientTlsStrategyBuilder.create().setSslContext(SSLContext.getDefault()).build())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(CONNECT_TIMEOUT)
                        .setSocketTimeout(SOCKET_TIMEOUT)
                        .build())
                .build();
        return ApacheHttpClient5TransportBuilder.builder(HttpHost.create(url))
                .setMapper(new JacksonJsonpMapper(mapper))
                .setHttpClientConfigCallback(builder -> builder.setConnectionManager(connectionManager))
                .build();
    }

    /** Indexing, rebuilds and the health check. */
    @Bean
    @Primary
    public OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }

    /** Keyword searches: give up on a busy pool or a slow answer instead of queueing. */
    @Bean
    public OpenSearchClient searchQueryClient(
            OpenSearchTransport transport,
            @Value("${app.opensearch.search.connection-request-timeout}") Duration connectionRequestTimeout,
            @Value("${app.opensearch.search.response-timeout}") Duration responseTimeout) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(connectionRequestTimeout))
                .setResponseTimeout(Timeout.of(responseTimeout))
                .build();
        return new OpenSearchClient(transport,
                ApacheHttpClient5Options.DEFAULT.toBuilder().setRequestConfig(requestConfig).build());
    }
}
