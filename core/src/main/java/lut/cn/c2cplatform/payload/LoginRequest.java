package lut.cn.c2cplatform.payload;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
    // Whether to sign in as administrator (true when "Administrator login" is ticked on the frontend)
    private Boolean isAdmin;
    private String captchaId;
    private String captchaCode;
}
