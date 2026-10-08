package sg.edu.nus.iss.c2csectrade.dto;

import lombok.Data;

@Data
public class PaymentRequest {
    private String paymentMethod;
    private String password;
}

