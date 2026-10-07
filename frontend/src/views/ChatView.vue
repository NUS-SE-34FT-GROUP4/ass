<template>
  <div class="chat-page">
    <div class="chat-layout">
      <!-- Left: user list -->
      <div class="users-sidebar">
        <div class="sidebar-header">
          <h3>Contacts</h3>
          <span v-if="unreadCount > 0" class="unread-badge">{{ unreadCount }}</span>
        </div>
        <div class="users-list">
          <div
            v-for="user in users"
            :key="user.id"
            @click="selectUser(user.username)"
            :class="['user-item', { active: selectedUsername === user.username }]"
          >
            <div class="user-avatar">{{ user.username.charAt(0).toUpperCase() }}</div>
            <div class="user-info">
              <div class="user-name">{{ user.username }}</div>
            </div>
          </div>
          <div v-if="users.length === 0" class="no-users">
            No contacts yet
          </div>
        </div>
      </div>

      <!-- Right: chat window -->
      <div class="chat-main">
        <ChatWindow
          v-if="selectedUsername"
          :recipientUsername="selectedUsername"
          :recipientDisplayName="selectedDisplayName"
          :currentUsername="currentUsername"
          @close="selectedUsername = null; selectedDisplayName = null"
        />
        <div v-else class="no-chat-selected">
          <div class="empty-state">
            <svg width="100" height="100" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
            </svg>
            <p>Choose a contact to start chatting</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import ChatWindow from './ChatWindow.vue';
import { chatService } from '@/services/chatService';
import axios from 'axios';
import { toast } from '@/services/toast';

const route = useRoute();
const users = ref([]);
const selectedUsername = ref(null);
const selectedDisplayName = ref(null);
const currentUsername = ref('');
const unreadCount = ref(0);

// Get the current user
const getCurrentUser = () => {
  const user = localStorage.getItem('user');
  if (user) {
    const userData = JSON.parse(user);
    currentUsername.value = userData.username || userData.name || '';
  }
};

// Load the user list
const fetchUsers = async () => {
  try {
    const token = localStorage.getItem('jwt_token');
    const response = await axios.get('/api/users', {
      headers: { Authorization: `Bearer ${token}` }
    });
    users.value = response.data.filter(u => u.username !== currentUsername.value);
  } catch (error) {
    console.error('Failed to fetch users:', error);
    toast('Failed to load contacts', 'error');
  }
};

// Load the unread count
const fetchUnreadCount = async () => {
  try {
    const response = await chatService.getUnreadCount();
    unreadCount.value = response.data;
  } catch (error) {
    console.error('Failed to fetch unread count:', error);
  }
};
// Select a user
const selectUser = (user) => {
  selectedUsername.value = user.username;
  selectedDisplayName.value = user.displayName || user.username;
  // Reload the unread count after switching user
  setTimeout(() => {
    fetchUnreadCount();
  }, 500);
};

onMounted(async () => {
  getCurrentUser();
  await fetchUsers();
  fetchUnreadCount();

  // If the URL has a recipientName parameter, select that user
  if (route.query.recipientName) {
    selectedUsername.value = route.query.recipientName;
  }

  // Refresh the unread count periodically
  setInterval(fetchUnreadCount, 30000); // Every 30 seconds
});
</script>

<style scoped>
.chat-page {
  min-height: 100vh;
  background: #f0f2f5;
  padding: 20px;
}

.chat-layout {
  display: flex;
  max-width: 1400px;
  margin: 0 auto;
  height: calc(100vh - 40px);
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.users-sidebar {
  width: 320px;
  border-right: 1px solid #ddd;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  background: #4CAF50;
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 18px;
}

.unread-badge {
  background: #ff5252;
  color: white;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: bold;
}

.users-list {
  flex: 1;
  overflow-y: auto;
}

.user-item {
  display: flex;
  align-items: center;
  padding: 15px 20px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid #f0f0f0;
}

.user-item:hover {
  background: #f5f5f5;
}

.user-item.active {
  background: #e8f5e9;
}

.user-avatar {
  width: 45px;
  height: 45px;
  border-radius: 50%;
  background: #4CAF50;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 18px;
  margin-right: 12px;
}

.user-info {
  flex: 1;
}

.user-name {
  font-weight: 500;
  font-size: 15px;
  color: #333;
}

.no-users {
  text-align: center;
  color: #999;
  padding: 50px 20px;
}

.chat-main {
  flex: 1;
  display: flex;
  align-items: stretch;
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
  margin-bottom: 20px;
  opacity: 0.3;
}

.empty-state p {
  font-size: 16px;
  margin: 0;
}
</style>
