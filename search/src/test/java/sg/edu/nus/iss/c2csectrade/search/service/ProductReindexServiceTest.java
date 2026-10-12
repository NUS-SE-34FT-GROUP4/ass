package sg.edu.nus.iss.c2csectrade.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.OperationType;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
import sg.edu.nus.iss.c2csectrade.event.Events;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductReindexServiceTest {

    private static final JacksonJsonpMapper MAPPER = new JacksonJsonpMapper();

    private final ProductCatalog catalog = mock(ProductCatalog.class);
    private final ProductIndexService indexService = mock(ProductIndexService.class);
    private final OpenSearchClient client = mock(OpenSearchClient.class);
    private final ProductReindexService service = new ProductReindexService(catalog, indexService, client, "products");

    @BeforeEach
    void setUp() throws IOException {
        when(client.indices()).thenReturn(mock(OpenSearchIndicesClient.class));
        when(client.bulk(any(BulkRequest.class))).thenReturn(bulkResponse(false));
        indexHolds();
    }

    @Test
    void indexesEveryProduct() throws Exception {
        when(catalog.findAll()).thenReturn(List.of(product(3), product(5)));

        ProductReindexService.Result result = service.rebuild();

        ArgumentCaptor<BulkRequest> bulk = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client).bulk(bulk.capture());
        assertThat(bulk.getValue().index()).isEqualTo("products");
        assertThat(bulk.getValue().operations()).allMatch(op -> op.isIndex())
                .extracting(op -> op.index().id())
                .containsExactly("3", "5");
        assertThat(result).isEqualTo(new ProductReindexService.Result(2, 0));
    }

    /**
     * 8 was deleted and its event lost; 9 was created while the rebuild ran,
     * so it is in the index but not in the rows read. Only 8 goes.
     */
    @Test
    void removesDocumentsWhoseProductIsGone() throws Exception {
        when(catalog.findAll()).thenReturn(List.of(product(3)));
        indexHolds("8", "9");
        when(catalog.existingIds(List.of(8L, 9L))).thenReturn(Set.of(9L));

        ProductReindexService.Result result = service.rebuild();

        ArgumentCaptor<BulkRequest> bulk = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(2)).bulk(bulk.capture());
        assertThat(bulk.getValue().operations()).allMatch(op -> op.isDelete())
                .extracting(op -> op.delete().id())
                .containsExactly("8");
        assertThat(result).isEqualTo(new ProductReindexService.Result(1, 1));
    }

    @Test
    void writesInBatches() throws Exception {
        int count = ProductReindexService.BATCH_SIZE * 2 + 1;
        when(catalog.findAll()).thenReturn(LongStream.rangeClosed(1, count).mapToObj(this::product).toList());

        assertThat(service.rebuild().indexed()).isEqualTo(count);
        verify(client, times(3)).bulk(any(BulkRequest.class));
    }

    @Test
    void looksForEveryDocumentNotJustRead() throws Exception {
        JsonNode request = json(service.notInRequest(List.of("3", "5")));

        assertThat(request.at("/query/bool/must_not/0/ids/values")).extracting(JsonNode::asText)
                .containsExactly("3", "5");
        assertThat(request.at("/_source").asBoolean()).isFalse();
        assertThat(request.at("/size").asInt()).isEqualTo(ProductReindexService.MAX_STALE);
    }

    @Test
    void emptyCatalogEmptiesTheIndex() throws Exception {
        when(catalog.findAll()).thenReturn(List.of());
        indexHolds("3");
        when(catalog.existingIds(List.of(3L))).thenReturn(Set.of());

        assertThat(service.rebuild()).isEqualTo(new ProductReindexService.Result(0, 1));
    }

    @Test
    void failedDocumentFailsTheRebuild() throws Exception {
        when(catalog.findAll()).thenReturn(List.of(product(3)));
        when(client.bulk(any(BulkRequest.class))).thenReturn(bulkResponse(true));

        assertThatThrownBy(service::rebuild).isInstanceOf(IOException.class).hasMessageContaining("3");
        verify(client, never()).search(any(SearchRequest.class), eq(Void.class));
    }

    @Test
    void secondRebuildWhileOneRunsIsRefused() throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(catalog.findAll()).thenAnswer(inv -> {
            reading.countDown();
            release.await();
            return List.of();
        });

        CompletableFuture<ProductReindexService.Result> first = CompletableFuture.supplyAsync(() -> {
            try {
                return service.rebuild();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        reading.await();

        assertThatThrownBy(service::rebuild).isInstanceOf(ProductReindexService.ReindexInProgressException.class);
        release.countDown();
        first.get();
        service.rebuild(); // and the next one runs again
    }

    @Test
    void startupFillsANewIndex() throws Exception {
        when(indexService.ensureIndex()).thenReturn(true);
        when(catalog.findAll()).thenReturn(List.of(product(3)));

        service.createIndexAtStartup();

        verify(client).bulk(any(BulkRequest.class));
    }

    @Test
    void startupLeavesAnExistingIndexAlone() throws Exception {
        when(indexService.ensureIndex()).thenReturn(false);

        service.createIndexAtStartup();

        verify(catalog, never()).findAll();
    }

    @Test
    void startupDoesNotFailWhenOpenSearchIsDown() throws Exception {
        when(indexService.ensureIndex()).thenThrow(new IOException("connection refused"));

        service.createIndexAtStartup();

        verify(catalog, never()).findAll();
    }

    private Events.ProductChangedEvent product(long id) {
        return new Events.ProductChangedEvent(id, 7L, "Desk lamp " + id, "Barely used", new BigDecimal("12.50"),
                9, "Kent Ridge", "Home", 1, LocalDateTime.of(2026, 10, 8, 9, 30));
    }

    private static BulkResponse bulkResponse(boolean failed) {
        return BulkResponse.of(r -> r
                .took(1)
                .errors(failed)
                .items(i -> {
                    i.operationType(OperationType.Index).index("products").id("3").status(failed ? 400 : 201);
                    if (failed) {
                        i.error(e -> e.type("mapper_parsing_exception").reason("bad price"));
                    }
                    return i;
                }));
    }

    private void indexHolds(String... ids) throws IOException {
        List<Hit<Void>> hits = Arrays.stream(ids).map(id -> Hit.<Void>of(h -> h.index("products").id(id))).toList();
        SearchResponse<Void> response = new SearchResponse.Builder<Void>()
                .took(1)
                .timedOut(false)
                .shards(sh -> sh.total(1).successful(1).failed(0))
                .hits(h -> h.hits(hits))
                .build();
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response);
    }

    private static JsonNode json(JsonpSerializable value) throws Exception {
        StringWriter out = new StringWriter();
        try (var generator = MAPPER.jsonProvider().createGenerator(out)) {
            value.serialize(generator, MAPPER);
        }
        return new ObjectMapper().readTree(out.toString());
    }
}
