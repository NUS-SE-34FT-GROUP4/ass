package sg.edu.nus.iss.c2csectrade.search.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import sg.edu.nus.iss.c2csectrade.search.service.ProductSearchQuery;
import sg.edu.nus.iss.c2csectrade.search.service.ProductSearchService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Against a local HTTP server standing in for an OpenSearch that answers
 * slowly: searches give up with an IOException, which SearchController
 * answers with 503, while indexing waits.
 */
class OpenSearchConfigTest {

    private static final Duration SLOW_ANSWER = Duration.ofMillis(1500);
    private static final ProductSearchQuery QUERY =
            new ProductSearchQuery("phone", null, null, null, null, null, 0, 20);

    private final OpenSearchConfig config = new OpenSearchConfig();
    private final CountDownLatch requestArrived = new CountDownLatch(1);
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private OpenSearchTransport transport;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            requestArrived.countDown();
            try {
                Thread.sleep(SLOW_ANSWER.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stop() throws IOException {
        if (transport != null) {
            transport.close();
        }
        server.stop(0);
    }

    @Test
    void searchGivesUpOnASlowAnswer() throws Exception {
        transport = transport(10);
        ProductSearchService search = searchService(Duration.ofMillis(500), Duration.ofMillis(300));

        long started = System.nanoTime();
        assertThatThrownBy(() -> search.search(QUERY)).isInstanceOf(IOException.class);
        assertThat(elapsed(started)).isLessThan(SLOW_ANSWER);
    }

    @Test
    void indexingWaitsForASlowAnswer() throws Exception {
        transport = transport(10);

        assertThat(config.openSearchClient(transport).ping().value()).isTrue();
    }

    /**
     * A search that finds every connection busy is dropped once its wait is
     * over, without reaching OpenSearch. The pool checks waits when a
     * connection is returned, which under load happens all the time; here
     * that is when the one slow request ends.
     */
    @Test
    void searchGivesUpWhenEveryConnectionIsBusy() throws Exception {
        transport = transport(1);
        OpenSearchClient indexing = config.openSearchClient(transport);
        ProductSearchService search = searchService(Duration.ofMillis(200), Duration.ofSeconds(5));

        CompletableFuture<?> busy = CompletableFuture.runAsync(() -> {
            try {
                indexing.ping();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(requestArrived.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> search.search(QUERY))
                .isInstanceOf(IOException.class)
                .hasRootCauseInstanceOf(TimeoutException.class);
        busy.get(5, TimeUnit.SECONDS);
        assertThat(requests).hasValue(1);
    }

    private ProductSearchService searchService(Duration connectionRequestTimeout, Duration responseTimeout) {
        return new ProductSearchService(
                config.searchQueryClient(transport, connectionRequestTimeout, responseTimeout), "products");
    }

    private OpenSearchTransport transport(int connections) throws Exception {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        return config.openSearchTransport(url, connections, connections);
    }

    private static Duration elapsed(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }
}
