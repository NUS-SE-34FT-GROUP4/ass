package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Product implements Serializable {
    private Long id;  // Long to match the database BIGINT type
    private Long userId;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer conditionLevel;
    private String location; // Location
    private String category; // Product category
    private int stock;
    private int status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProductMedia> media;
}
