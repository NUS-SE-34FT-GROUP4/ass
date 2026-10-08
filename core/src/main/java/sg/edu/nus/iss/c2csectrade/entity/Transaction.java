package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class Transaction {
    private Integer id;
    private Integer orderId;
    private BigDecimal amount;
    private String transactionType;
    private String paymentMethod;
    private String status;
    private Date createdAt;
}
