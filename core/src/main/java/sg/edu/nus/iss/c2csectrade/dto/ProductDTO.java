package sg.edu.nus.iss.c2csectrade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductDTO {
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private String avatarUrl; // Seller avatar URL
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Integer conditionLevel;
    private String location;
    private String category; // Product category
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<String> imageUrls;
    private List<String> videoUrls;
    private String coverImage;
    private List<MediaItem> media; // Media list so the frontend can show multiple images

    // Highlighting fields
    private String highlightedName;
    private String highlightedDescription;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaItem {
        private Long id;
        private String url;
        private Integer mediaType; // 1 = image, 2 = video
        private Integer sortOrder;
    }
}
