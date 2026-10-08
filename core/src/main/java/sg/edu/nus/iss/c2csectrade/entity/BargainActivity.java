package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class BargainActivity {
    private Long id;
    private Long userId;
    private Long productId;
    private BigDecimal originalPrice;
    private BigDecimal targetPrice;
    private BigDecimal currentPrice;
    private String status; // ACTIVE, SUCCESS, EXPIRED
    private Date expireTime;
    private Date createdAt;
    private Date updatedAt;

    // Joined data
    private String username;
    private String displayName;
    private String productName;
    private String productImage;
    private Integer helpCount; // Number of helpers
}

