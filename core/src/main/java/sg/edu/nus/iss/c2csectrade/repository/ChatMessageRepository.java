package sg.edu.nus.iss.c2csectrade.repository;

import sg.edu.nus.iss.c2csectrade.entity.ChatMessage;
import sg.edu.nus.iss.c2csectrade.mapper.ChatMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ChatMessageRepository {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    public void save(ChatMessage chatMessage) {
        chatMessageMapper.insert(chatMessage);
    }

    public List<ChatMessage> findHistoryBetweenUsers(Long userId1, Long userId2, int limit) {
        return chatMessageMapper.findHistoryBetweenUsers(userId1, userId2, limit);
    }

    public void markAsRead(Long senderId, Long recipientId) {
        chatMessageMapper.markAsRead(senderId, recipientId);
    }

    public int countUnreadMessages(Long recipientId) {
        return chatMessageMapper.countUnreadMessages(recipientId);
    }

    public List<String> findConversationPartners(Long userId) {
        return chatMessageMapper.findConversationPartners(userId);
    }

    public ChatMessage findLastMessageBetweenUsers(Long userId1, Long userId2) {
        return chatMessageMapper.findLastMessageBetweenUsers(userId1, userId2);
    }

    public int countUnreadMessagesBetweenUsers(Long recipientId, Long senderId) {
        return chatMessageMapper.countUnreadMessagesBetweenUsers(recipientId, senderId);
    }

    public List<ChatMessage> findSystemMessages(Long recipientId, int limit) {
        return chatMessageMapper.findSystemMessages(recipientId, limit);
    }

    public void markSystemMessagesAsRead(Long recipientId) {
        chatMessageMapper.markSystemMessagesAsRead(recipientId);
    }

    public ChatMessage findLastSystemMessage(Long recipientId) {
        return chatMessageMapper.findLastSystemMessage(recipientId);
    }

    public int countUnreadSystemMessages(Long recipientId) {
        return chatMessageMapper.countUnreadSystemMessages(recipientId);
    }
}
