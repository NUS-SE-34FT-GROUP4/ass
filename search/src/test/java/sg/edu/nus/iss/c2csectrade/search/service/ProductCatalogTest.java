package sg.edu.nus.iss.c2csectrade.search.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import sg.edu.nus.iss.c2csectrade.event.Events;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductCatalogTest {

    /** A row reads into the same event Core publishes, so both paths index the same document. */
    @Test
    void rowCarriesEveryIndexedField() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 8, 9, 30);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(42L);
        when(rs.getLong("user_id")).thenReturn(7L);
        when(rs.getString("name")).thenReturn("Desk lamp");
        when(rs.getString("description")).thenReturn("Barely used");
        when(rs.getBigDecimal("price")).thenReturn(new BigDecimal("12.50"));
        when(rs.getObject("condition_level", Integer.class)).thenReturn(9);
        when(rs.getString("location")).thenReturn("Kent Ridge");
        when(rs.getString("category")).thenReturn("Home");
        when(rs.getObject("status", Integer.class)).thenReturn(1);
        when(rs.getObject("created_at", LocalDateTime.class)).thenReturn(createdAt);

        assertThat(ProductCatalog.ROW_MAPPER.mapRow(rs, 0)).isEqualTo(new Events.ProductChangedEvent(
                42L, 7L, "Desk lamp", "Barely used", new BigDecimal("12.50"),
                9, "Kent Ridge", "Home", 1, createdAt));
    }

    @Test
    void asksWhichIdsStillExist() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(ProductCatalog.SELECT_EXISTING, Map.of("ids", List.of(8L, 9L)), Long.class))
                .thenReturn(List.of(9L));

        assertThat(new ProductCatalog(jdbc).existingIds(List.of(8L, 9L))).containsExactly(9L);
    }

    @Test
    void noIdsNeedNoQuery() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);

        assertThat(new ProductCatalog(jdbc).existingIds(List.of())).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    void readsOnlyTheProductTable() {
        assertThat(ProductCatalog.SELECT_ALL).endsWith("FROM pms_product");
    }
}
