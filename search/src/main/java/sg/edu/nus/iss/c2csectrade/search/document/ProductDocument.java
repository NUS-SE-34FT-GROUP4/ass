package sg.edu.nus.iss.c2csectrade.search.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A product in the index, and an item in a search response. The field
 * mapping is in ProductIndexService; the JSON field names are the index
 * field names, so do not rename them without reindexing. Unknown fields are
 * ignored: documents Core wrote through Spring Data Elasticsearch carry _class.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDocument {
    private String id;
    private Long productId;
    private Long userId;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer conditionLevel;
    private String location;
    private Integer status;

    // Whole seconds: the first format of the createdAt mapping, which the
    // documents Core wrote through Spring Data Elasticsearch also use
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private String category;

    // Search results only; never stored in the index
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String highlightedName;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String highlightedDescription;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getConditionLevel() {
        return conditionLevel;
    }

    public void setConditionLevel(Integer conditionLevel) {
        this.conditionLevel = conditionLevel;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getHighlightedName() {
        return highlightedName;
    }

    public void setHighlightedName(String highlightedName) {
        this.highlightedName = highlightedName;
    }

    public String getHighlightedDescription() {
        return highlightedDescription;
    }

    public void setHighlightedDescription(String highlightedDescription) {
        this.highlightedDescription = highlightedDescription;
    }
}
