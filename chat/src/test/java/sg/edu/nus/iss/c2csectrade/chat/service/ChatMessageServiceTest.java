package sg.edu.nus.iss.c2csectrade.chat.service;

import sg.edu.nus.iss.c2csectrade.chat.dto.ConversationDTO;
import sg.edu.nus.iss.c2csectrade.chat.entity.ChatMessage;
import sg.edu.nus.iss.c2csectrade.chat.entity.User;
import sg.edu.nus.iss.c2csectrade.chat.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.chat.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every message is saved before it is fanned out, so a user who was offline
 * reads it in their history. System messages, such as a delisting notice,
 * have no sender and are read under the partner name "System".
 * Buyer 7 chats with seller 9.
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    void aMessageIsSavedUnreadWithBothParties() {
        when(userMapper.selectByUsername("buyer7")).thenReturn(user(7L, "buyer7"));
        when(userMapper.selectByUsername("seller9")).thenReturn(user(9L, "seller9"));

        ChatMessage message = chatMessageService.saveMessage("buyer7", "seller9", "Is it still available?", false);

        assertThat(message.getSenderId()).isEqualTo(7L);
        assertThat(message.getRecipientId()).isEqualTo(9L);
        assertThat(message.getIsRead()).isFalse();
        assertThat(message.getIsSystemMessage()).isFalse();
        assertThat(message.getTimestamp()).isNotNull();
        verify(chatMessageRepository).save(message);
    }

    @Test
    void aSystemMessageHasNoSender() {
        when(userMapper.selectByUsername("seller9")).thenReturn(user(9L, "seller9"));

        ChatMessage message = chatMessageService.saveMessage("System", "seller9", "Your listing was delisted", true);

        assertThat(message.getSenderId()).isNull();
        assertThat(message.getIsSystemMessage()).isTrue();
        verify(userMapper, never()).selectByUsername("System");
        verify(chatMessageRepository).save(message);
    }

    @Test
    void aMessageToAnUnknownUserIsNotSaved() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);

        assertThatThrownBy(() -> chatMessageService.saveMessage("buyer7", "ghost", "Hi", false))
                .hasMessageContaining("Recipient not found");
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void aMessageFromAnUnknownUserIsNotSaved() {
        when(userMapper.selectByUsername("seller9")).thenReturn(user(9L, "seller9"));
        when(userMapper.selectByUsername("ghost")).thenReturn(null);

        assertThatThrownBy(() -> chatMessageService.saveMessage("ghost", "seller9", "Hi", false))
                .hasMessageContaining("Sender not found");
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void historyIsReadBetweenTheTwoUsers() {
        signedIn7And9();
        List<ChatMessage> history = List.of(new ChatMessage());
        when(chatMessageRepository.findHistoryBetweenUsers(7L, 9L, 50)).thenReturn(history);

        assertThat(chatMessageService.getChatHistory("buyer7", "seller9", 50)).isSameAs(history);
    }

    @Test
    void systemHistoryIsReadFromTheSystemMessages() {
        when(userMapper.selectByUsername("seller9")).thenReturn(user(9L, "seller9"));
        List<ChatMessage> notices = List.of(new ChatMessage());
        when(chatMessageRepository.findSystemMessages(9L, 20)).thenReturn(notices);

        assertThat(chatMessageService.getChatHistory("seller9", "System", 20)).isSameAs(notices);
    }

    @Test
    void historyWithAnUnknownUserFails() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        when(userMapper.selectByUsername("seller9")).thenReturn(user(9L, "seller9"));

        assertThatThrownBy(() -> chatMessageService.getChatHistory("ghost", "System", 20))
                .hasMessageContaining("User not found");
        assertThatThrownBy(() -> chatMessageService.getChatHistory("seller9", "ghost", 20))
                .hasMessageContaining("User not found");
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void openingAConversationMarksItsMessagesRead() {
        signedIn7And9();

        chatMessageService.markMessagesAsRead("buyer7", "seller9");
        chatMessageService.markMessagesAsRead("System", "seller9");

        verify(chatMessageRepository).markAsRead(7L, 9L);
        verify(chatMessageRepository).markSystemMessagesAsRead(9L);
    }

    @Test
    void markingReadForAnUnknownUserFails() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        when(userMapper.selectByUsername("seller9")).thenReturn(user(9L, "seller9"));

        assertThatThrownBy(() -> chatMessageService.markMessagesAsRead("System", "ghost"))
                .hasMessageContaining("Recipient not found");
        assertThatThrownBy(() -> chatMessageService.markMessagesAsRead("ghost", "seller9"))
                .hasMessageContaining("User not found");
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void theUnreadCountIsTheRecipients() {
        when(userMapper.selectByUsername("seller9")).thenReturn(user(9L, "seller9"));
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        when(chatMessageRepository.countUnreadMessages(9L)).thenReturn(3);

        assertThat(chatMessageService.getUnreadCount("seller9")).isEqualTo(3);
        assertThatThrownBy(() -> chatMessageService.getUnreadCount("ghost")).hasMessageContaining("User not found");
    }

    @Test
    void conversationsShowTheLastMessageAndUnreadCount() {
        User buyer = user(7L, "buyer7");
        buyer.setDisplayName("Buyer Seven");
        buyer.setAvatarUrl("https://cdn.example/b7.png");
        User plain = user(8L, "plain8");
        when(userMapper.selectByUsername("seller9")).thenReturn(user(9L, "seller9"));
        when(userMapper.selectByUsername("buyer7")).thenReturn(buyer);
        when(userMapper.selectByUsername("plain8")).thenReturn(plain);
        when(userMapper.selectByUsername("gone")).thenReturn(null);
        when(chatMessageRepository.findConversationPartners(9L)).thenReturn(List.of("buyer7", "gone", "plain8"));
        ChatMessage last = ChatMessage.builder().content("Deal").timestamp(new Date()).build();
        when(chatMessageRepository.findLastMessageBetweenUsers(9L, 7L)).thenReturn(last);
        when(chatMessageRepository.findLastMessageBetweenUsers(9L, 8L)).thenReturn(null);
        when(chatMessageRepository.countUnreadMessagesBetweenUsers(9L, 7L)).thenReturn(2);
        when(chatMessageRepository.countUnreadMessagesBetweenUsers(9L, 8L)).thenReturn(0);

        List<ConversationDTO> conversations = chatMessageService.getConversations("seller9");

        assertThat(conversations).extracting(ConversationDTO::getUsername).containsExactly("buyer7", "plain8");
        ConversationDTO first = conversations.get(0);
        assertThat(first.getDisplayName()).isEqualTo("Buyer Seven");
        assertThat(first.getAvatar()).isEqualTo("https://cdn.example/b7.png");
        assertThat(first.getLastMessage()).isEqualTo("Deal");
        assertThat(first.getUnreadCount()).isEqualTo(2);
        ConversationDTO second = conversations.get(1);
        assertThat(second.getDisplayName()).isEqualTo("plain8");
        assertThat(second.getAvatar()).contains("name=plain8");
        assertThat(second.getLastMessage()).isEmpty();
        assertThat(second.getLastMessageTime()).isNull();
    }

    @Test
    void conversationsOfAnUnknownUserFail() {
        when(userMapper.selectByUsername("ghost")).thenReturn(null);

        assertThatThrownBy(() -> chatMessageService.getConversations("ghost")).hasMessageContaining("User not found");
    }

    @Test
    void everyUserIsListedForABroadcast() {
        List<User> users = List.of(user(7L, "buyer7"));
        when(userMapper.selectAll()).thenReturn(users);

        assertThat(chatMessageService.getAllUsers()).isSameAs(users);
    }

    private void signedIn7And9() {
        when(userMapper.selectByUsername("buyer7")).thenReturn(user(7L, "buyer7"));
        when(userMapper.selectByUsername("seller9")).thenReturn(user(9L, "seller9"));
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
