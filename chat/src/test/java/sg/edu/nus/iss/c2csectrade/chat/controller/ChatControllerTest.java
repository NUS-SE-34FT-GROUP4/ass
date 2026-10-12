package sg.edu.nus.iss.c2csectrade.chat.controller;

import sg.edu.nus.iss.c2csectrade.chat.dto.ChatMessageDTO;
import sg.edu.nus.iss.c2csectrade.chat.entity.ChatMessage;
import sg.edu.nus.iss.c2csectrade.chat.entity.User;
import sg.edu.nus.iss.c2csectrade.chat.event.ChatMessageFanoutPublisher;
import sg.edu.nus.iss.c2csectrade.chat.service.ChatMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatMessageFanoutPublisher chatMessageFanoutPublisher;

    @Mock
    private ChatMessageService chatMessageService;

    @InjectMocks
    private ChatController chatController;

    private Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Authentication userAuth() {
        return new UsernamePasswordAuthenticationToken("buyer1", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void broadcastSendsToAllUsersExceptTheAdmin() {
        User u1 = new User(); u1.setUsername("user1");
        User u2 = new User(); u2.setUsername("user2");
        User admin = new User(); admin.setUsername("admin");
        when(chatMessageService.getAllUsers()).thenReturn(List.of(u1, u2, admin));

        ChatMessage saved = new ChatMessage();
        saved.setContent("Maintenance tonight");
        saved.setTimestamp(new Date());
        when(chatMessageService.saveMessage(eq("System"), anyString(), anyString(), eq(true)))
                .thenReturn(saved);

        ResponseEntity<?> response = chatController.broadcastSystemMessage(
                Map.of("message", "Maintenance tonight"), adminAuth());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // Two non-admin users should each get a message
        verify(chatMessageFanoutPublisher, times(2)).deliverToUser(anyString(), any(ChatMessageDTO.class));
        verify(chatMessageFanoutPublisher).deliverToUser(eq("user1"), any(ChatMessageDTO.class));
        verify(chatMessageFanoutPublisher).deliverToUser(eq("user2"), any(ChatMessageDTO.class));
        verify(chatMessageFanoutPublisher, never()).deliverToUser(eq("admin"), any(ChatMessageDTO.class));
    }

    @Test
    void broadcastRejectsNonAdmin() {
        ResponseEntity<?> response = chatController.broadcastSystemMessage(
                Map.of("message", "hello"), userAuth());
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(chatMessageFanoutPublisher);
    }

    @Test
    void broadcastRejectsEmptyMessage() {
        ResponseEntity<?> response = chatController.broadcastSystemMessage(
                Map.of("message", "  "), adminAuth());
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(chatMessageFanoutPublisher);
    }

    @Test
    void sendSystemMessagePersistsAndFansOut() {
        ChatMessage saved = new ChatMessage();
        saved.setContent("Warning");
        saved.setTimestamp(new Date());
        when(chatMessageService.saveMessage(eq("System"), eq("user1"), eq("Warning"), eq(true)))
                .thenReturn(saved);

        ChatMessageDTO dto = ChatMessageDTO.builder()
                .sender("System").recipient("user1").content("Warning")
                .timestamp(new Date()).isSystemMessage(true).build();

        ResponseEntity<?> response = chatController.sendSystemMessage(dto, adminAuth());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(chatMessageFanoutPublisher).deliverToUser(eq("user1"), any(ChatMessageDTO.class));
    }

    @Test
    void sendSystemMessageRejectsNonAdmin() {
        ChatMessageDTO dto = ChatMessageDTO.builder()
                .sender("System").recipient("user1").content("hi").build();
        ResponseEntity<?> response = chatController.sendSystemMessage(dto, userAuth());
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(chatMessageFanoutPublisher);
    }
}
