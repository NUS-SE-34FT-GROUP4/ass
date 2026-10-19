package sg.edu.nus.iss.c2csectrade.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {
    private Long userId;
    private String username;
    private String displayName;
    private String avatar;
    private String lastMessage;
    private Date lastMessageTime;
    private Integer unreadCount;
    private Boolean isOnline;
}

