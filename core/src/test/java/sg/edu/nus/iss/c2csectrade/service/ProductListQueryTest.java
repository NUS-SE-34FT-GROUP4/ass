package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import sg.edu.nus.iss.c2csectrade.config.MinioProperties;
import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The product list against a real MySQL with the WP10 load-test volume
 * (10,000 listings): paging, the total, sorting, and loading a page's media
 * and sellers in one query each. Listing all of them used to take 8.4 s.
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProductService.class)
@Testcontainers
class ProductListQueryTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("trade")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("..", "init.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/init.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?serverTimezone=Asia/Singapore");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @MockitoBean FileStorageService fileStorageService;
    @MockitoBean MinioProperties minioProperties;
    @MockitoBean ApplicationEventPublisher eventPublisher;

    @Autowired ProductService products;
    @Autowired JdbcTemplate jdbc;

    private long existing;

    /** 10,000 listings from one seller, prices 1..100, two images on every 10th. */
    @BeforeEach
    void seed() {
        // Counted with JDBC: a mapper call here would sit in MyBatis's session cache,
        // which these JDBC inserts do not invalidate, and be returned again later.
        existing = jdbc.queryForObject("SELECT COUNT(*) FROM pms_product WHERE status = 1 AND stock > 0", Long.class);
        jdbc.update("INSERT INTO users (id, username, password_hash, email, balance, enabled) "
                + "VALUES (2000000, 'list_seller', 'x', 'list_seller@example.invalid', 0, 1)");
        jdbc.execute("SET SESSION cte_max_recursion_depth = 20000");
        jdbc.update("INSERT INTO pms_product (id, user_id, name, description, price, stock, condition_level, "
                + "location, category, status, created_at, updated_at) "
                + "WITH RECURSIVE n(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 9999) "
                + "SELECT 2000000 + i, 2000000, CONCAT('list item ', i), 'fixture', 1 + i % 100, 1, 9, "
                + "'Singapore', 'other', 1, TIMESTAMPADD(SECOND, i, '2030-01-01'), NOW() FROM n");
        jdbc.update("INSERT INTO pms_product_media (url, media_type, sort_order, product_id) "
                + "SELECT CONCAT('https://img.invalid/', id, '-', s.o), 1, s.o, id FROM pms_product "
                + "CROSS JOIN (SELECT 0 o UNION ALL SELECT 1) s WHERE id >= 2000000 AND id % 10 = 0");
    }

    @Test
    void returnsOnePageNewestFirstWithTheTotal() {
        ProductService.ProductPage first = products.listProductsPage(null, null, null, null, null, null,
                "default", 0, 12);
        ProductService.ProductPage second = products.listProductsPage(null, null, null, null, null, null,
                "default", 1, 12);

        assertThat(first.total()).isEqualTo(existing + 10_000);
        assertThat(first.items()).hasSize(12);
        // Seeded created_at is in 2030, so the newest are the last ids inserted
        assertThat(first.items()).extracting(ProductDTO::getId)
                .containsExactly(2009999L, 2009998L, 2009997L, 2009996L, 2009995L, 2009994L,
                        2009993L, 2009992L, 2009991L, 2009990L, 2009989L, 2009988L);
        assertThat(second.items().get(0).getId()).isEqualTo(2009987L);
    }

    @Test
    void loadsSellersAndMediaForTheWholePage() {
        List<ProductDTO> page = products.listProductsPage(null, null, null, null, null, null,
                "default", 0, 12).items();

        assertThat(page).allSatisfy(p -> assertThat(p.getUsername()).isEqualTo("list_seller"));
        ProductDTO withImages = page.stream().filter(p -> p.getId() == 2009990L).findFirst().orElseThrow();
        assertThat(withImages.getImageUrls()).containsExactly(
                "https://img.invalid/2009990-0", "https://img.invalid/2009990-1");
        assertThat(withImages.getCoverImage()).isEqualTo("https://img.invalid/2009990-0");
        assertThat(page.stream().filter(p -> p.getId() == 2009991L).findFirst().orElseThrow().getMedia())
                .isNullOrEmpty();
    }

    @Test
    void sortsAcrossAllMatchesNotWithinAPage() {
        List<ProductDTO> cheapest = products.listProductsPage("list item", null, null, null, null, null,
                "price-asc", 0, 5).items();
        List<ProductDTO> dearest = products.listProductsPage("list item", null, null, null, null, null,
                "price-desc", 0, 5).items();

        assertThat(cheapest).extracting(ProductDTO::getPrice).allMatch(p -> p.compareTo(BigDecimal.ONE) == 0);
        assertThat(dearest).extracting(ProductDTO::getPrice).allMatch(p -> p.compareTo(new BigDecimal("100")) == 0);
    }

    @Test
    void filtersApplyToTheCountAsWellAsThePage() {
        ProductService.ProductPage page = products.listProductsPage(null, new BigDecimal("99"), null, null, null,
                null, "default", 0, 100);

        assertThat(page.total()).isGreaterThanOrEqualTo(200); // prices 99 and 100: 100 each
        assertThat(page.items()).hasSize(100).allMatch(p -> p.getPrice().compareTo(new BigDecimal("99")) >= 0);
    }

    @Test
    void aPageBeyondTheEndIsEmptyButKeepsTheTotal() {
        ProductService.ProductPage page = products.listProductsPage(null, null, null, null, null, null,
                "default", 100_000, 12);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(existing + 10_000);
    }

    /** The load test's budget is 2 s per request; a page should be far inside it. */
    @Test
    void aPageOfTenThousandListingsIsFast() {
        products.listProductsPage(null, null, null, null, null, null, "default", 0, 12); // warm up
        long start = System.nanoTime();
        ProductService.ProductPage page = products.listProductsPage(null, null, null, null, null, null,
                "default", 0, 12);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(page.items()).hasSize(12);
        assertThat(millis).as("one page of 12 out of 10,000, in ms").isLessThan(500);
    }

    @Test
    void searchHitsKeepTheirOrderAndSkipWhatIsNoLongerListed() {
        jdbc.update("UPDATE pms_product SET status = 0 WHERE id = 2000002");
        List<ProductDTO> hits = products.getListedProductDTOs(List.of(2000003L, 2000002L, 2000001L, 9_999_999L));

        assertThat(hits).extracting(ProductDTO::getId).containsExactly(2000003L, 2000001L);
        assertThat(hits).allSatisfy(p -> assertThat(p.getUsername()).isEqualTo("list_seller"));
    }

    @Test
    void noSearchHitsOrOnlyUnlistedOnesLoadNothing() {
        jdbc.update("UPDATE pms_product SET status = 0 WHERE id IN (2000004, 2000005)");

        assertThat(products.getListedProductDTOs(List.of())).isEmpty();
        assertThat(products.getListedProductDTOs(List.of(2000004L, 2000005L))).isEmpty();
    }

    /** The detail page converts one product on its own and still gets its seller and images. */
    @Test
    void aSingleProductCarriesItsSellerAndMedia() {
        ProductDTO detail = products.getProductDTOById(2000010L);

        assertThat(detail.getUsername()).isEqualTo("list_seller");
        assertThat(detail.getImageUrls()).containsExactly("https://img.invalid/2000010-0", "https://img.invalid/2000010-1");
    }
}
