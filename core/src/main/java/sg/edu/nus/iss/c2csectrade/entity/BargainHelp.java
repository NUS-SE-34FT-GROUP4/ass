package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class BargainHelp {
    private Long id;
    private Long bargainId;
    private Long helperId;
    private String helperName;
    private BigDecimal cutAmount;
    private Date createdAt;

    // Joined data
    private String helperAvatar;
}

