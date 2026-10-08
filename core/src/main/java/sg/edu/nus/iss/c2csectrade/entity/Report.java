package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Report {
    private Long id;
    private Long productId;
    private Long reporterId;
    private String reason;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
