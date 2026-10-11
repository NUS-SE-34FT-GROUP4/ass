package sg.edu.nus.iss.c2csectrade.search.service;

import java.math.BigDecimal;

/**
 * Search criteria; every one is optional.
 *
 * @param category one category, or several separated by commas
 */
public record ProductSearchQuery(
        String keyword,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer conditionLevel,
        String location,
        String category,
        int page,
        int size) {
}
