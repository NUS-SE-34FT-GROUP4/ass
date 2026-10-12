package sg.edu.nus.iss.c2csectrade.search.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import sg.edu.nus.iss.c2csectrade.event.Events;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads products straight from Core's pms_product table, for the full index
 * rebuild only. Search logs in as search_svc, which may only SELECT this
 * table (infra/db/grants.sql); day-to-day indexing comes from product events.
 */
@Repository
public class ProductCatalog {

    static final String SELECT_ALL = "SELECT id, user_id, name, description, price, condition_level,"
            + " location, category, status, created_at FROM pms_product";

    // The same fields as a product event, so both paths write identical documents
    static final RowMapper<Events.ProductChangedEvent> ROW_MAPPER = (rs, rowNum) -> new Events.ProductChangedEvent(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBigDecimal("price"),
            rs.getObject("condition_level", Integer.class),
            rs.getString("location"),
            rs.getString("category"),
            rs.getObject("status", Integer.class),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public ProductCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Events.ProductChangedEvent> findAll() {
        return jdbc.query(SELECT_ALL, ROW_MAPPER);
    }

    /** Which of these product ids exist now. */
    public Set<Long> existingIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return new HashSet<>(jdbc.queryForList(
                "SELECT id FROM pms_product WHERE id IN (" + placeholders + ")", Long.class, ids.toArray()));
    }
}
