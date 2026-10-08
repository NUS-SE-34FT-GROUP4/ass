package sg.edu.nus.iss.c2csectrade.dto;

import lombok.Data;

@Data
public class SetPaymentPasswordRequest {
    private String password;
    private String confirmPassword;
}

