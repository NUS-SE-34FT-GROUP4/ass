<template>
  <div class="floating-chat-container">
    <!-- Chat bubble button -->
    <div
      class="chat-bubble"
      @click="toggleChat"
      :class="{ 'has-unread': totalUnreadCount > 0 }"
    >
      <!-- Message bubble icon -->
      <svg class="chat-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M20 2H4C2.9 2 2 2.9 2 4V22L6 18H20C21.1 18 22 17.1 22 16V4C22 2.9 21.1 2 20 2Z" fill="currentColor"/>
        <circle cx="7" cy="9" r="1" fill="white"/>
        <circle cx="12" cy="9" r="1" fill="white"/>
        <circle cx="17" cy="9" r="1" fill="white"/>
      </svg>

      <!-- Total unread badge -->
      <div v-if="totalUnreadCount > 0" class="unread-badge">
        {{ totalUnreadCount > 99 ? '99+' : totalUnreadCount }}
      </div>
    </div>

    <!-- Chat window -->
    <div v-if="showChat" class="chat-window-large">
      <!-- Close button, top right -->
      <button @click="closeChat" class="chat-close-btn">&times;</button>

      <!-- Left: conversation list or user list -->
      <div class="chat-sidebar">
        <div class="sidebar-header">
          <h3>💬 Messages</h3>
        </div>

        <!-- Administrator: user picker -->
        <div v-if="isAdmin && !currentChatUser" class="user-selection">
          <div class="search-bar">
            <input
              v-model="userSearchKeyword"
              @input="filterUsers"
              type="text"
              placeholder="Search users..."
              class="search-input"
            />
          </div>
          <div v-if="loadingUsers" class="loading-state">
            <div class="spinner"></div>
            <p>Loading users...</p>
          </div>
          <div v-else class="user-list">
            <div
              v-for="user in filteredUsers"
              :key="user.id"
              class="user-item"
              @click="selectUserToChat(user)"
            >
              <img :src="user.avatar" :alt="user.username" class="user-avatar">
              <div class="user-info">
                <div class="user-name">{{ user.displayName || user.username }}</div>
                <div class="user-status">{{ user.username }}</div>
              </div>
            </div>
            <div v-if="filteredUsers.length === 0" class="no-users">
              No users found
            </div>
          </div>
        </div>

        <!-- Regular user, or administrator with a user selected: conversation list -->
        <div v-else class="conversation-section">
          <!-- Administrator with a user selected: back button -->
          <div v-if="isAdmin && currentChatUser" class="current-chat-header">
            <button @click="backToUserList" class="back-btn">← Back</button>
            <div class="current-user-info">
              <img :src="currentChatUser.avatar" :alt="currentChatUser.username" class="small-avatar">
              <span>{{ currentChatUser.displayName || currentChatUser.username }}</span>
            </div>
          </div>

          <div v-if="loading" class="loading-conversations">
            <div class="spinner"></div>
            <p>Loading...</p>
          </div>
          <div v-else-if="conversations.length === 0" class="no-conversations">
            <div class="empty-icon">📭</div>
            <p>No conversations yet</p>
          </div>
          <div v-else class="conversation-list">
            <div
              v-for="conv in conversations"
              :key="conv.userId"
              class="conversation-item"
              :class="{ 'active': selectedConversation?.userId === conv.userId, 'has-unread': conv.unreadCount > 0 }"
              @click="selectConversation(conv)"
            >
              <div class="conv-avatar-wrapper">
                <img :src="conv.avatar" :alt="conv.username" class="conv-avatar">
                <div v-if="conv.isOnline" class="online-indicator"></div>
              </div>
              <div class="conv-info">
                <div class="conv-header">
                  <div class="conv-name">{{ conv.displayName || conv.username }}</div>
                  <div class="conv-time">{{ formatTime(conv.lastMessageTime) }}</div>
                </div>
                <div class="conv-last-message">
                  {{ conv.lastMessage || 'No messages yet' }}
                </div>
              </div>
              <div v-if="conv.unreadCount > 0" class="conv-unread">
                {{ conv.unreadCount > 99 ? '99+' : conv.unreadCount }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: chat area -->
      <div class="chat-main">
        <div v-if="selectedConversation || currentChatUser" class="chat-area">
          <ChatWindow
            :recipientUsername="(selectedConversation?.username || currentChatUser?.username)"
            :recipientDisplayName="(selectedConversation?.displayName || currentChatUser?.displayName || selectedConversation?.username || currentChatUser?.username)"
            :currentUsername="currentUsername"
          />
        </div>
        <div v-else class="no-chat-selected">
          <div class="empty-state">
            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
            </svg>
            <p>{{ isAdmin ? 'Choose a user to start chatting' : 'Choose a conversation to start chatting' }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import toast from '@/utils/toast';

import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useAuthStore } from '@/store/auth';
import ChatWindow from '@/views/ChatWindow.vue';
import axios from 'axios';
import WebSocketService from '@/services/WebSocketService';
import emitter from '@/eventBus';

const authStore = useAuthStore();
const showChat = ref(false);
const conversations = ref([]);
const loading = ref(false);
const loadingUsers = ref(false);
const selectedConversation = ref(null);
const currentChatUser = ref(null);
const allUsers = ref([]);
const filteredUsers = ref([]);
const userSearchKeyword = ref('');
const currentUsername = ref('');
let refreshInterval = null;

// WebSocket connection
const onMessageReceived = (payload) => {
  try {
    console.log('[WS] Message received:', payload.body);
    const message = JSON.parse(payload.body);
    console.log('[WS] Parsed message:', message);

    // Emit the message event for ChatWindow
    emitter.emit('chat-message', message);

    // Refresh the conversation list to update unread counts
    if (!isAdmin.value || (isAdmin.value && !currentChatUser.value)) {
      loadConversations();
    }
  } catch (error) {
    console.error('[WS] Failed to handle message:', error);
  }
};

onMounted(() => {
  if (authStore.isLoggedIn) {
    WebSocketService.connect(onMessageReceived);
    getCurrentUser();
    if (!isAdmin.value) {
      loadConversations();
      startRefreshInterval();
    }
  }

  // Listen for the global open-chat event
  window.addEventListener('open-chat', handleOpenChatEvent);
});

onUnmounted(() => {
  WebSocketService.disconnect();
  stopRefreshInterval();
  window.removeEventListener('open-chat', handleOpenChatEvent);
});

// Whether the user is an administrator
const isAdmin = computed(() => {
  return authStore.user?.roles?.includes('ROLE_ADMIN') || false;
});

// Total unread messages
const totalUnreadCount = computed(() =>
  conversations.value.reduce((total, conv) => total + conv.unreadCount, 0)
);

const toggleChat = () => {
  if (!authStore.isLoggedIn) {
    toast.warning('Please sign in first');
    return;
  }
  showChat.value = !showChat.value;
  if (showChat.value) {
    getCurrentUser();
    if (isAdmin.value) {
      loadUsers();
    } else {
      loadConversations();
    }
  }
};

const closeChat = () => {
  showChat.value = false;
  selectedConversation.value = null;
  currentChatUser.value = null;
};

// Load the conversation list
const loadConversations = async () => {
  loading.value = true;
  try {
    const token = localStorage.getItem('jwt_token');
    if (!token) return;

    const response = await axios.get('/api/chat/conversations', {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    // Process the returned conversation list
    conversations.value = response.data.map(conv => ({
      userId: conv.userId,
      username: conv.username,
      displayName: conv.displayName || conv.username,
      avatar: conv.avatarUrl || conv.avatar || `https://ui-avatars.com/api/?name=${encodeURIComponent(conv.displayName || conv.username)}&background=007bff&color=fff&size=100`,
      lastMessage: conv.lastMessage || '',
      lastMessageTime: conv.lastMessageTime,
      unreadCount: conv.unreadCount || 0,
      isOnline: conv.isOnline || false
    }));
  } catch (error) {
    console.error('Failed to load conversations:', error);
    // If the API is not implemented yet, use mock data
    conversations.value = [];
  } finally {
    loading.value = false;
  }
};

// Format the time for display
const formatTime = (timestamp) => {
  if (!timestamp) return '';

  const now = new Date();
  const time = new Date(timestamp);
  const diff = now - time;

  // Within a minute
  if (diff < 60000) {
    return 'Just now';
  }
  // Within an hour
  if (diff < 3600000) {
    return `${Math.floor(diff / 60000)} min ago`;
  }
  // Today
  if (now.toDateString() === time.toDateString()) {
    return time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  // Yesterday
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  if (yesterday.toDateString() === time.toDateString()) {
    return 'Yesterday';
  }
  // Earlier
  return time.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
};

const getCurrentUser = () => {
  const user = localStorage.getItem('user');
  if (user) {
    const userData = JSON.parse(user);
    currentUsername.value = userData.username || userData.name || '';
  }
};

// Load all users (administrator feature)
const loadUsers = async () => {
  loadingUsers.value = true;
  try {
    const token = localStorage.getItem('jwt_token');
    const response = await axios.get('/api/users', {
      headers: { Authorization: `Bearer ${token}` }
    });
    allUsers.value = response.data
      .filter(u => u.username !== currentUsername.value)
      .map(user => ({
        ...user,
        displayName: user.displayName || user.username,
        avatar: user.avatarUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(user.displayName || user.username)}&background=007bff&color=fff&size=100`
      }));
    filteredUsers.value = allUsers.value;
  } catch (error) {
    console.error('Failed to load users:', error);
  } finally {
    loadingUsers.value = false;
  }
};

// Filter the user list
const filterUsers = () => {
  const keyword = userSearchKeyword.value.toLowerCase().trim();
  if (!keyword) {
    filteredUsers.value = allUsers.value;
  } else {
    filteredUsers.value = allUsers.value.filter(user =>
      user.username.toLowerCase().includes(keyword) ||
      (user.displayName && user.displayName.toLowerCase().includes(keyword))
    );
  }
};

// Choose a user to chat with (administrator feature)
const selectUserToChat = (user) => {
  // Make sure the user object has everything needed, including the avatar
  currentChatUser.value = {
    ...user,
    avatar: user.avatar || user.avatarUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(user.displayName || user.username)}&background=007bff&color=fff&size=100`
  };
  selectedConversation.value = null;
  loadConversations();
};

// Back to the user list (administrator feature)
const backToUserList = () => {
  currentChatUser.value = null;
  selectedConversation.value = null;
  userSearchKeyword.value = '';
  filteredUsers.value = allUsers.value;
};

// Select a conversation
const selectConversation = async (conv) => {
  selectedConversation.value = conv;
  currentChatUser.value = null;

  // Clear this conversation's unread count immediately
  if (conv.unreadCount > 0) {
    conv.unreadCount = 0;

    // Mark as read through the API
    try {
      const token = localStorage.getItem('jwt_token');
      await axios.post(`/api/chat/mark-read/${conv.username}`, {}, {
        headers: { Authorization: `Bearer ${token}` }
      });
    } catch (error) {
      console.error('Failed to mark as read:', error);
    }
  }
};

// Refresh the conversation list periodically
const startRefreshInterval = () => {
  refreshInterval = setInterval(() => {
    if (authStore.isLoggedIn && showChat.value && !isAdmin.value) {
      loadConversations();
    }
  }, 10000); // Every 10 seconds
};

const stopRefreshInterval = () => {
  if (refreshInterval) {
    clearInterval(refreshInterval);
    refreshInterval = null;
  }
};

// Handle the open-chat event
const handleOpenChatEvent = async (event) => {
  const { username, displayName, userId, avatarUrl } = event.detail;

  // Open the chat window
  showChat.value = true;

  // Create or select the conversation
  const existingConv = conversations.value.find(c => c.username === username);
  if (existingConv) {
    selectedConversation.value = existingConv;

    // Clear the unread count immediately
    if (existingConv.unreadCount > 0) {
      existingConv.unreadCount = 0;

      // Mark as read through the API
      try {
        const token = localStorage.getItem('jwt_token');
        await axios.post(`/api/chat/mark-read/${username}`, {}, {
          headers: { Authorization: `Bearer ${token}` }
        });
      } catch (error) {
        console.error('Failed to mark as read:', error);
      }
    }
  } else {
    // For a new conversation, try to fetch the user's avatar from the backend
    const finalDisplayName = displayName || username;
    let userAvatar = avatarUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(finalDisplayName)}&background=007bff&color=fff&size=100`;

    // If no avatarUrl was passed, look it up in the user list
    if (!avatarUrl) {
      try {
        const token = localStorage.getItem('jwt_token');
        const response = await axios.get(`/api/users/${username}`, {
          headers: { Authorization: `Bearer ${token}` }
        });
        if (response.data && response.data.avatarUrl) {
          userAvatar = response.data.avatarUrl;
        }
      } catch (error) {
        console.log('Could not load user avatar, using the default');
      }
    }

    selectedConversation.value = {
      userId: userId,
      username: username,
      displayName: finalDisplayName,
      avatar: userAvatar,
      lastMessage: '',
      lastMessageTime: null,
      unreadCount: 0,
      isOnline: false
    };
    // Add the new conversation to the top of the list
    conversations.value.unshift(selectedConversation.value);
  }

  currentChatUser.value = null;
};

onMounted(() => {
  if (authStore.isLoggedIn) {
    getCurrentUser();
    if (!isAdmin.value) {
      loadConversations();
      startRefreshInterval();
    }
  }

  // Listen for the global open-chat event
  window.addEventListener('open-chat', handleOpenChatEvent);
});

onUnmounted(() => {
  stopRefreshInterval();
  window.removeEventListener('open-chat', handleOpenChatEvent);
});
</script>

<style scoped>
.floating-chat-container {
  position: fixed;
  bottom: 30px;
  right: 30px;
  z-index: 999;
}

.chat-bubble {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
  transition: all 0.3s ease;
  position: relative;
}

.chat-bubble:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.5);
}

.chat-bubble.has-unread {
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4); }
  50% { box-shadow: 0 4px 20px rgba(102, 126, 234, 0.8); }
}

.chat-icon {
  width: 32px;
  height: 32px;
  color: white;
}

.unread-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  background: #ff4444;
  color: white;
  border-radius: 12px;
  padding: 2px 6px;
  font-size: 12px;
  font-weight: bold;
  min-width: 20px;
  text-align: center;
}

/* Large chat window */
.chat-window-large {
  position: fixed;
  bottom: 100px;
  right: 30px;
  width: 900px;
  height: 650px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  overflow: hidden;
  animation: slideUp 0.3s ease-out;
}

/* Close button, top right */
.chat-close-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1000;
  background: rgba(255, 255, 255, 0.9);
  border: none;
  color: #666;
  font-size: 32px;
  cursor: pointer;
  padding: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  line-height: 1;
}

.chat-close-btn:hover {
  background: #ff4444;
  color: white;
  transform: rotate(90deg);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Left sidebar */
.chat-sidebar {
  width: 320px;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  background: #f8f9fa;
  overflow: hidden;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  color: white;
  font-size: 28px;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background 0.2s;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

/* User picker */
.user-selection {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.search-bar {
  padding: 12px;
  border-bottom: 1px solid #e0e0e0;
}

.search-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
}

.search-input:focus {
  border-color: #667eea;
}

.user-list {
  flex: 1;
  overflow-y: auto;
}

.user-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid #f0f0f0;
}

.user-item:hover {
  background: #e8eaf6;
}

.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  margin-right: 12px;
  object-fit: cover;
}

.user-info {
  flex: 1;
}

.user-name {
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.user-status {
  font-size: 12px;
  color: #666;
}

/* Conversation list */
.conversation-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.current-chat-header {
  display: flex;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #e0e0e0;
  background: white;
}

.back-btn {
  background: none;
  border: none;
  color: #667eea;
  font-size: 14px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 6px;
  margin-right: 8px;
  transition: background 0.2s;
}

.back-btn:hover {
  background: #f0f0f0;
}

.current-user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.small-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid #f0f0f0;
}

.conversation-item:hover {
  background: #f5f5f5;
}

.conversation-item.active {
  background: #e8eaf6;
}

.conversation-item.has-unread {
  background: #f0f7ff;
}

.conv-avatar-wrapper {
  position: relative;
  margin-right: 12px;
}

.conv-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
}

.online-indicator {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 12px;
  height: 12px;
  background: #4caf50;
  border: 2px solid white;
  border-radius: 50%;
}

.conv-info {
  flex: 1;
  min-width: 0;
}

.conv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.conv-name {
  font-weight: 600;
  color: #333;
  font-size: 14px;
}

.conv-time {
  font-size: 12px;
  color: #999;
}

.conv-last-message {
  font-size: 13px;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-unread {
  background: #ff4444;
  color: white;
  border-radius: 12px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: bold;
  min-width: 20px;
  text-align: center;
  margin-left: 8px;
}

/* Right chat area */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.no-chat-selected {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-state {
  text-align: center;
  color: #999;
}

.empty-state svg {
  color: #ddd;
  margin-bottom: 16px;
}

.empty-state p {
  font-size: 16px;
  margin: 0;
}

/* Loading state */
.loading-conversations,
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #999;
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 12px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.no-conversations,
.no-users {
  text-align: center;
  padding: 40px 20px;
  color: #999;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

/* Responsive */
@media (max-width: 1024px) {
  .chat-window-large {
    width: 700px;
    height: 550px;
  }

  .chat-sidebar {
    width: 280px;
  }
}

@media (max-width: 768px) {
  .chat-window-large {
    width: 90vw;
    height: 80vh;
    left: 5vw;
    bottom: 80px;
  }

  .chat-sidebar {
    width: 100%;
  }

  .chat-main {
    display: none;
  }
}
</style>
