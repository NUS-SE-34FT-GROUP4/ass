package sg.edu.nus.iss.c2csectrade.dto;

import lombok.Data;

@Data
public class BuyNowRequest {
    private Long productId;
    private Integer quantity;
}

