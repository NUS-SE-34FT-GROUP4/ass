<template>
  <div class="admin-chat-page">
    <div class="page-header">
      <button @click="goHome" class="home-btn">🏠 Back to Home</button>
      <h1>📢 System Message Center</h1>
      <p class="subtitle">Send system notices and announcements to users</p>
    </div>

    <div class="admin-chat-layout">
      <!-- Left: user list -->
      <div class="users-sidebar">
        <div class="sidebar-header">
          <h3>Choose Recipients</h3>
        </div>

        <!-- Search box -->
        <div class="search-box">
          <span class="search-icon">🔍</span>
          <input
            v-model="searchKeyword"
            @input="filterUsers"
            type="text"
            placeholder="Search users..."
            class="search-input"
          />
        </div>

        <!-- Shortcut button -->
        <div class="quick-actions">
          <button
            @click="selectAllUsers"
            :class="['quick-btn', { active: sendToAll }]">
            📣 All Users ({{ users.length }})
          </button>
        </div>

        <!-- User list -->
        <div class="users-list">
          <div
            v-for="user in filteredUsers"
            :key="user.id"
            @click="selectUser(user)"
            :class="['user-item', { active: selectedUsername === user.username }]"
          >
            <img :src="getUserAvatar(user)" :alt="user.username" class="user-avatar" />
            <div class="user-info">
              <div class="user-name">{{ user.displayName || user.username }}</div>
              <div class="user-username">@{{ user.username }}</div>
            </div>
          </div>
          <div v-if="filteredUsers.length === 0" class="no-users">
            <div class="empty-icon">👤</div>
            <p>No users found</p>
          </div>
        </div>
      </div>

      <!-- Right: send a system message -->
      <div class="chat-main">
        <div v-if="selectedUsername" class="system-message-panel">
          <div class="panel-header">
            <h3>Send system message to: {{ selectedUsername }}</h3>
          </div>

          <div class="message-preview">
            <div class="preview-label">Preview:</div>
            <div class="preview-content system-message-preview">
              <div class="message-badge">🔔 System Message</div>
              <div class="message-text">{{ systemMessage || '(Type your message)' }}</div>
            </div>
          </div>

          <form @submit.prevent="sendSystemMessage" class="message-form">
            <label>Message:</label>
            <textarea
              v-model="systemMessage"
              placeholder="Type the system message..."
              class="message-textarea"
              rows="6"
              required
            ></textarea>

            <div class="form-actions">
              <button type="submit" class="send-btn" :disabled="!systemMessage.trim() || sending">
                {{ sending ? 'Sending...' : 'Send System Message' }}
              </button>
              <button type="button" @click="clearMessage" class="clear-btn">
                Clear
              </button>
              <button type="button" @click="sendSystemMessageToAll" class="send-all-btn" :disabled="!systemMessage.trim() || sending">
                Send to Everyone
              </button>
            </div>
          </form>

          <div class="tips">
            <div class="tip-title">💡 Tips:</div>
            <ul>
              <li>System messages are highlighted in the user's chat</li>
              <li>Users get a real-time push notification</li>
              <li>Users cannot reply to system messages</li>
            </ul>
          </div>
        </div>

        <div v-else class="no-user-selected">
          <div class="empty-state">
            <svg width="100" height="100" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
            </svg>
            <p>Choose a user to send a system message to</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';
import { toast } from '@/services/toast';

const router = useRouter();

const users = ref([]);
const selectedUsername = ref(null);
const selectedUser = ref(null);
const systemMessage = ref('');
const sending = ref(false);
const searchKeyword = ref('');
const sendToAll = ref(false);

// Filtered user list
const filteredUsers = computed(() => {
  if (!searchKeyword.value.trim()) {
    return users.value;
  }
  const keyword = searchKeyword.value.toLowerCase();
  return users.value.filter(u =>
    u.username.toLowerCase().includes(keyword) ||
    u.email.toLowerCase().includes(keyword) ||
    (u.displayName && u.displayName.toLowerCase().includes(keyword))
  );
});

// Load the user list
const fetchUsers = async () => {
  try {
    const token = localStorage.getItem('jwt_token');
    const response = await axios.get('/api/users', {
      headers: { Authorization: `Bearer ${token}` }
    });

    // Exclude the current administrator
    const currentUser = JSON.parse(localStorage.getItem('user'));
    users.value = response.data.filter(u => u.username !== currentUser.username);
  } catch (error) {
    console.error('Failed to fetch users:', error);
    toast('Failed to load users', 'error');
  }
};

// Get a user's avatar
const getUserAvatar = (user) => {
  if (user.avatarUrl) {
    return user.avatarUrl;
  }
  const name = user.displayName || user.username;
  return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=ff5722&color=fff&size=100`;
};

// Select a user
const selectUser = (user) => {
  sendToAll.value = false;
  selectedUsername.value = user.username;
  selectedUser.value = user;
  systemMessage.value = '';
};

// Select all users (bulk send)
const selectAllUsers = () => {
  sendToAll.value = true;
  selectedUsername.value = 'All users';
  selectedUser.value = null;
  systemMessage.value = '';
};

// Filter users
const filterUsers = () => {
  // Reactivity handles this; nothing else to do
};

// Back to home
const goHome = () => {
  router.push({ name: 'home' });
};

// Send a system message
const sendSystemMessage = async () => {
  if (!systemMessage.value.trim() || sending.value) {
    return;
  }

  sending.value = true;

  try {
    const token = localStorage.getItem('jwt_token');

    if (sendToAll.value) {
      // Send to every user
      const sendPromises = users.value.map(user =>
        axios.post('/api/chat/admin/send-system-message', {
          recipient: user.username,
          content: systemMessage.value
        }, {
          headers: { Authorization: `Bearer ${token}` }
        }).catch(err => {
          console.error(`Failed to send to ${user.username}:`, err);
          return { error: true, username: user.username };
        })
      );

      const results = await Promise.all(sendPromises);
      const failedCount = results.filter(r => r.error).length;

      if (failedCount === 0) {
        toast(`Sent to ${users.value.length} users`, 'success');
      } else {
        toast(`Done: ${users.value.length - failedCount} sent, ${failedCount} failed`, 'warning');
      }
    } else {
      // Send to a single user
      await axios.post('/api/chat/admin/send-system-message', {
        recipient: selectedUsername.value,
        content: systemMessage.value
      }, {
        headers: { Authorization: `Bearer ${token}` }
      });

      toast('System message sent', 'success');
    }

    systemMessage.value = '';
  } catch (error) {
    console.error('Failed to send system message:', error);
    const errorMsg = error.response?.data || 'Send failed';
    toast(errorMsg, 'error');
  } finally {
    sending.value = false;
  }
};

// Send to everyone
const sendSystemMessageToAll = async () => {
  if (!systemMessage.value.trim() || sending.value) return;

  if (!confirm(`Send this message to all ${users.value.length} users?`)) {
    return;
  }

  sending.value = true;
  try {
    const token = localStorage.getItem('jwt_token');
    const response = await axios.post('/api/message/broadcast', {
      message: systemMessage.value
    }, {
      headers: { Authorization: `Bearer ${token}` }
    });

    toast(response.data || 'System message sent to all users', 'success');
    systemMessage.value = '';
  } catch (error) {
    console.error('Broadcast failed:', error);
    const errorMsg = error.response?.data || 'Broadcast failed';
    toast(errorMsg, 'error');
  } finally {
    sending.value = false;
  }
};

// Clear the message
const clearMessage = () => {
  systemMessage.value = '';
};

onMounted(() => {
  fetchUsers();
});
</script>

<style scoped>
.admin-chat-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #ff6b6b 0%, #ee5a6f 100%);
  padding: 20px;
}

.page-header {
  background: white;
  border-radius: 16px;
  padding: 30px;
  margin-bottom: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  text-align: center;
  position: relative;
}

.home-btn {
  position: absolute;
  top: 20px;
  left: 20px;
  padding: 10px 20px;
  background: linear-gradient(135deg, #ff6b6b 0%, #ee5a6f 100%);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.3);
}

.home-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 107, 107, 0.4);
}

.page-header h1 {
  margin: 0 0 8px 0;
  color: #333;
  font-size: 28px;
  font-weight: 600;
}

.subtitle {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.admin-chat-layout {
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
  background: #ff5722;
  color: white;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 18px;
}

/* Search box */
.search-box {
  position: relative;
  padding: 15px;
  border-bottom: 1px solid #e0e0e0;
}

.search-icon {
  position: absolute;
  left: 25px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 16px;
  color: #999;
}

.search-input {
  width: 100%;
  padding: 10px 10px 10px 35px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.3s;
}

.search-input:focus {
  border-color: #ff5722;
}

/* Shortcut button */
.quick-actions {
  padding: 15px;
  border-bottom: 1px solid #e0e0e0;
}

.quick-btn {
  width: 100%;
  padding: 12px;
  background: white;
  border: 2px solid #ff5722;
  color: #ff5722;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.quick-btn:hover {
  background: #fff3f0;
}

.quick-btn.active {
  background: #ff5722;
  color: white;
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
  background: #ffe8e0;
}

.user-avatar {
  width: 45px;
  height: 45px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #ff5722;
  margin-right: 12px;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-weight: 500;
  font-size: 15px;
  color: #333;
  margin-bottom: 4px;
}

.user-email {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-username {
  font-size: 12px;
  color: #ff5722;
  font-weight: 500;
}

.no-users {
  text-align: center;
  color: #999;
  padding: 50px 20px;
}

.no-users .empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.no-users p {
  font-size: 14px;
  margin: 0;
}

.chat-main {
  flex: 1;
  display: flex;
  align-items: stretch;
}

.system-message-panel {
  flex: 1;
  padding: 30px;
  overflow-y: auto;
}

.panel-header {
  margin-bottom: 30px;
}

.panel-header h3 {
  margin: 0;
  font-size: 24px;
  color: #333;
  display: flex;
  align-items: center;
  gap: 10px;
}

.message-preview {
  margin-bottom: 30px;
  padding: 20px;
  background: #f9f9f9;
  border-radius: 8px;
  border: 1px solid #eee;
}

.preview-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
  font-weight: 500;
}

.system-message-preview {
  background: #fff3cd;
  border-left: 4px solid #ffa000;
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.message-badge {
  font-size: 12px;
  font-weight: bold;
  color: #f57c00;
  margin-bottom: 8px;
}

.message-text {
  color: #333;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.message-form {
  margin-bottom: 30px;
}

.message-form label {
  display: block;
  margin-bottom: 10px;
  font-weight: 500;
  color: #333;
}

.message-textarea {
  width: 100%;
  padding: 15px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  outline: none;
  transition: border-color 0.2s;
}

.message-textarea:focus {
  border-color: #ff5722;
}

.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 15px;
}

.send-btn {
  flex: 1;
  padding: 12px 30px;
  background: #ff5722;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.send-btn:hover:not(:disabled) {
  background: #e64a19;
}

.send-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.clear-btn {
  padding: 12px 30px;
  background: #f5f5f5;
  color: #666;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.clear-btn:hover {
  background: #e0e0e0;
  border-color: #ccc;
}

.tips {
  padding: 20px;
  background: #e3f2fd;
  border-radius: 8px;
  border-left: 4px solid #2196f3;
}

.tip-title {
  font-weight: bold;
  color: #1976d2;
  margin-bottom: 10px;
}

.tips ul {
  margin: 0;
  padding-left: 20px;
  color: #555;
}

.tips li {
  margin-bottom: 8px;
  line-height: 1.6;
}

.no-user-selected {
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
