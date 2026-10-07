<template>
  <div class="admin-users">
    <div class="page-header">
      <button @click="goHome" class="home-btn">🏠 Back to Home</button>
      <div class="header-content">
        <h1>👥 User Management</h1>
        <p class="subtitle">View and manage all registered users</p>
      </div>
    </div>

    <!-- Stat cards -->
    <div class="stats-cards">
      <div class="stat-card">
        <div class="stat-icon">👤</div>
        <div class="stat-info">
          <div class="stat-label">Total Users</div>
          <div class="stat-value">{{ totalUsers }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">👑</div>
        <div class="stat-info">
          <div class="stat-label">Administrators</div>
          <div class="stat-value">{{ adminCount }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">⛔</div>
        <div class="stat-info">
          <div class="stat-label">Suspended</div>
          <div class="stat-value">{{ suspendedCount }}</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">🆕</div>
        <div class="stat-info">
          <div class="stat-label">New Today</div>
          <div class="stat-value">{{ todayNewUsers }}</div>
        </div>
      </div>
    </div>

    <!-- Search and filter -->
    <div class="search-section">
      <div class="search-bar">
        <span class="search-icon">🔍</span>
        <input
          v-model="searchKeyword"
          @input="handleSearch"
          type="text"
          placeholder="Search username or email..."
          class="search-input"
        />
      </div>
      <div class="filter-buttons">
        <button
          :class="['filter-btn', { active: roleFilter === 'all' }]"
          @click="roleFilter = 'all'">
          All Users
        </button>
        <button
          :class="['filter-btn', { active: roleFilter === 'admin' }]"
          @click="roleFilter = 'admin'">
          Administrators
        </button>
        <button
          :class="['filter-btn', { active: roleFilter === 'user' }]"
          @click="roleFilter = 'user'">
          Regular Users
        </button>
        <button
          :class="['filter-btn', { active: roleFilter === 'suspended' }]"
          @click="roleFilter = 'suspended'">
          Suspended
        </button>
      </div>
    </div>

    <!-- User list -->
    <div class="users-container">
      <div v-if="loading" class="loading-state">
        <div class="spinner"></div>
        <p>Loading...</p>
      </div>
      <div v-else-if="error" class="error-state">
        <div class="error-icon">⚠️</div>
        <p>{{ error }}</p>
        <button @click="fetchUsers" class="retry-btn">Retry</button>
      </div>
      <div v-else-if="filteredUsers.length === 0" class="empty-state">
        <div class="empty-icon">📭</div>
        <p>No matching users</p>
      </div>
      <div v-else class="users-grid">
        <div v-for="user in filteredUsers" :key="user.id" :class="['user-card', { suspended: isSuspended(user) }]">
          <div class="user-header">
            <img :src="getUserAvatar(user)" :alt="user.username" class="user-avatar" />
            <div class="user-badges">
              <span v-if="isAdmin(user)" class="badge badge-admin">👑 Admin</span>
              <span v-else class="badge badge-user">👤 User</span>
              <span v-if="isSuspended(user)" class="badge badge-suspended">⛔ Suspended</span>
            </div>
          </div>
          <div class="user-body">
            <h3 class="user-name">{{ user.displayName || user.username }}</h3>
            <p class="user-username">@{{ user.username }}</p>
            <div class="user-info">
              <div class="info-item">
                <span class="info-icon">📧</span>
                <span class="info-text">{{ user.email }}</span>
              </div>
              <div class="info-item">
                <span class="info-icon">🆔</span>
                <span class="info-text">ID: {{ user.id }}</span>
              </div>
            </div>
          </div>
          <div class="user-actions">
            <button @click="sendMessage(user)" class="action-btn primary">
              💬 Message
            </button>
            <button @click="viewUserProducts(user)" class="action-btn">
              📦 View Products
            </button>
            <!-- Administrator accounts are managed outside the back office -->
            <template v-if="!isAdmin(user)">
              <button @click="openEditUsername(user)" class="action-btn">
                ✏️ Rename
              </button>
              <button v-if="isSuspended(user)" @click="reinstateUser(user)" class="action-btn">
                ✅ Reinstate
              </button>
              <button v-else @click="suspendUser(user)" class="action-btn warning">
                ⛔ Suspend
              </button>
              <button @click="deleteUser(user)" class="action-btn danger">
                🗑️ Delete
              </button>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- Rename dialog -->
    <template v-if="showEditDialog">
      <div class="edit-dialog-overlay" @click="closeEditDialog">
        <div class="edit-dialog" @click.stop>
          <h2>✏️ Change Display Name</h2>
          <div class="edit-dialog-header">
            <span class="edit-dialog-label">Current display name</span>
            <span class="edit-dialog-current">{{ editingUser?.displayName || editingUser?.username }}</span>
          </div>
          <div class="edit-dialog-divider"></div>
          <div class="edit-input-group">
            <label class="edit-input-label">New display name</label>
            <input v-model="editUsername" placeholder="Enter a new display name..." class="edit-input" />
            <div class="edit-dialog-tip">💡 Up to 100 characters, not used by another user</div>
          </div>
          <div class="edit-dialog-actions">
            <button @click="confirmEditUsername" class="confirm-btn" :disabled="!editUsername.trim()">
              ✓ Confirm
            </button>
            <button @click="closeEditDialog" class="cancel-btn">
              ✕ Cancel
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';
import { toast } from '@/services/toast';

const router = useRouter();
const users = ref([]);
const loading = ref(false);
const error = ref('');
const searchKeyword = ref('');
const roleFilter = ref('all');
const showEditDialog = ref(false);
const editUsername = ref('');
const editingUser = ref(null);

const fetchUsers = async () => {
  loading.value = true;
  error.value = '';
  try {
    const { data } = await axios.get('/api/admin/users');
    users.value = data;
  } catch (e) {
    error.value = 'Failed to load users';
    console.error(e);
  } finally {
    loading.value = false;
  }
};

// Statistics
const totalUsers = computed(() => users.value.length);

const adminCount = computed(() => {
  return users.value.filter(u => isAdmin(u)).length;
});

const suspendedCount = computed(() => users.value.filter(u => isSuspended(u)).length);

const todayNewUsers = computed(() => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return users.value.filter(u => {
    if (u.createdAt) {
      const userDate = new Date(u.createdAt);
      return userDate >= today;
    }
    return false;
  }).length;
});

// Filter users
const filteredUsers = computed(() => {
  let result = users.value;

  // Role filter
  if (roleFilter.value === 'admin') {
    result = result.filter(u => isAdmin(u));
  } else if (roleFilter.value === 'user') {
    result = result.filter(u => !isAdmin(u));
  } else if (roleFilter.value === 'suspended') {
    result = result.filter(u => isSuspended(u));
  }

  // Keyword filter
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.toLowerCase();
    result = result.filter(u =>
      u.username.toLowerCase().includes(keyword) ||
      u.email.toLowerCase().includes(keyword) ||
      (u.displayName && u.displayName.toLowerCase().includes(keyword))
    );
  }

  return result;
});

// Whether the user is an administrator
const isAdmin = (user) => {
  return user.roles && user.roles.some(r =>
    r.name === 'ROLE_ADMIN' || r.name === 'ADMIN'
  );
};

const isSuspended = (user) => user.enabled === false;

const errorMessage = (e, fallback) => e.response?.data?.message || fallback;

// Get a user's avatar
const getUserAvatar = (user) => {
  if (user.avatarUrl) {
    return user.avatarUrl;
  }
  const name = user.displayName || user.username;
  return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=007bff&color=fff&size=100`;
};

// Search
const handleSearch = () => {
  // Search is reactive; nothing else to do
};

// Back to home
const goHome = () => {
  router.push({ name: 'home' });
};

// Send a message
const sendMessage = (user) => {
  // Open the chat bubble and select the user
  window.dispatchEvent(new CustomEvent('open-chat', {
    detail: {
      username: user.username,
      displayName: user.displayName || user.username,
      userId: user.id
    }
  }));
};

// Go to the user's products page
const viewUserProducts = (user) => {
  router.push({
    name: 'user-products',
    params: { userId: user.id },
    query: { username: user.username, displayName: user.displayName }
  });
};

// Delete a user
const deleteUser = async (user) => {
  if (!confirm(`Delete user ${user.username}? This cannot be undone and all of their data will be deleted.`)) {
    return;
  }

  try {
    await axios.delete(`/api/admin/users/${user.id}`);
    toast('User deleted', 'success');
    fetchUsers();
  } catch (e) {
    console.error('Failed to delete user:', e);
    toast(errorMessage(e, 'Failed to delete user, please try again'), 'error');
  }
};

// Suspend: the user can no longer sign in, and their current session stops working
const suspendUser = async (user) => {
  if (!confirm(`Suspend ${user.username}? They will be signed out and unable to sign in until reinstated.`)) {
    return;
  }
  try {
    await axios.put(`/api/admin/users/${user.id}/suspend`);
    toast(`${user.username} suspended`, 'success');
    fetchUsers();
  } catch (e) {
    toast(errorMessage(e, 'Failed to suspend user'), 'error');
  }
};

const reinstateUser = async (user) => {
  try {
    await axios.put(`/api/admin/users/${user.id}/reinstate`);
    toast(`${user.username} reinstated`, 'success');
    fetchUsers();
  } catch (e) {
    toast(errorMessage(e, 'Failed to reinstate user'), 'error');
  }
};

// Open the rename dialog
const openEditUsername = (user) => {
  editingUser.value = user;
  editUsername.value = user.displayName || user.username;
  showEditDialog.value = true;
};

// Close the dialog
const closeEditDialog = () => {
  showEditDialog.value = false;
  editUsername.value = '';
  editingUser.value = null;
};

// Confirm the rename
const confirmEditUsername = async () => {
  if (!editUsername.value.trim()) return;
  try {
    await axios.put(`/api/admin/users/${editingUser.value.id}/display-name`, {
      displayName: editUsername.value.trim()
    });
    toast('Display name updated', 'success');
    fetchUsers();
    closeEditDialog();
  } catch (e) {
    toast(errorMessage(e, 'Failed to update display name'), 'error');
  }
};

onMounted(fetchUsers);
</script>

<style scoped>
.admin-users {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.page-header {
  background: white;
  border-radius: 16px;
  padding: 30px;
  margin-bottom: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  position: relative;
}

.home-btn {
  position: absolute;
  top: 20px;
  left: 20px;
  padding: 10px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.home-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.header-content {
  text-align: center;
}

.header-content h1 {
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

/* Stat cards */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 15px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-icon {
  font-size: 40px;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 13px;
  color: #666;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #333;
}

/* Search and filter */
.search-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.search-bar {
  position: relative;
  margin-bottom: 15px;
}

.search-icon {
  position: absolute;
  left: 15px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 18px;
}

.search-input {
  width: 100%;
  padding: 12px 12px 12px 45px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  transition: border-color 0.3s;
}

.search-input:focus {
  outline: none;
  border-color: #667eea;
}

.filter-buttons {
  display: flex;
  gap: 10px;
}

.filter-btn {
  padding: 8px 16px;
  border: 2px solid #e0e0e0;
  background: white;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s;
}

.filter-btn:hover {
  border-color: #667eea;
  color: #667eea;
}

.filter-btn.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-color: transparent;
}

/* User container */
.users-container {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  min-height: 400px;
}

/* Loading / error / empty states */
.loading-state,
.error-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #666;
}

.spinner {
  width: 50px;
  height: 50px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error-icon,
.empty-icon {
  font-size: 60px;
  margin-bottom: 16px;
}

.retry-btn {
  margin-top: 16px;
  padding: 10px 24px;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.3s;
}

.retry-btn:hover {
  background: #764ba2;
}

/* User grid */
.users-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.user-card {
  background: white;
  border: 2px solid #e0e0e0;
  border-radius: 12px;
  padding: 20px;
  transition: all 0.3s;
}

.user-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
  border-color: #667eea;
}

.user-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 15px;
}

.user-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #667eea;
}

.user-badges {
  display: flex;
  gap: 5px;
}

.badge {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.badge-admin {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.badge-user {
  background: #e0e0e0;
  color: #666;
}

.badge-suspended {
  background: #ffb020;
  color: white;
}

.user-card.suspended {
  opacity: 0.75;
  border-style: dashed;
}

.user-body {
  margin-bottom: 15px;
}

.user-name {
  margin: 0 0 4px 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.user-username {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #667eea;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #666;
}

.info-icon {
  font-size: 16px;
}

.info-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.user-actions .action-btn {
  min-width: calc(50% - 5px);
}

.action-btn {
  flex: 1;
  padding: 10px;
  border: 2px solid #e0e0e0;
  background: white;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.3s;
}

.action-btn:hover {
  border-color: #667eea;
  color: #667eea;
}

.action-btn.primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-color: transparent;
}

.action-btn.primary:hover {
  transform: scale(1.05);
}

.action-btn.danger {
  background: #ff4444;
  color: white;
  border-color: transparent;
}

.action-btn.danger:hover {
  background: #cc0000;
  transform: scale(1.05);
}

.action-btn.warning {
  background: #ffb020;
  color: white;
  border-color: transparent;
}

.action-btn.warning:hover {
  background: #e09000;
}

/* Rename dialog */
.edit-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(5px);
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.edit-dialog {
  background: white;
  border-radius: 20px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  min-width: 450px;
  max-width: 500px;
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.edit-dialog h2 {
  margin: 0 0 24px 0;
  font-size: 24px;
  font-weight: 700;
  color: #333;
  text-align: center;
}

.edit-dialog-header {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e9ecef 100%);
  border-radius: 12px;
  margin-bottom: 8px;
}

.edit-dialog-label {
  font-weight: 600;
  color: #666;
  font-size: 14px;
}

.edit-dialog-current {
  font-weight: 700;
  color: #667eea;
  background: white;
  border-radius: 8px;
  padding: 6px 16px;
  border: 2px solid #667eea;
  font-size: 15px;
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.edit-dialog-divider {
  height: 2px;
  background: linear-gradient(90deg, transparent, #e0e0e0, transparent);
  margin: 20px 0;
}

.edit-input-group {
  margin-bottom: 28px;
}

.edit-input-label {
  display: block;
  font-weight: 600;
  color: #333;
  font-size: 14px;
  margin-bottom: 10px;
}

.edit-input {
  width: 100%;
  padding: 14px 18px;
  border: 2px solid #e0e0e0;
  border-radius: 12px;
  font-size: 16px;
  outline: none;
  transition: all 0.3s;
  box-sizing: border-box;
  font-family: inherit;
}

.edit-input:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 4px rgba(102, 126, 234, 0.1);
  background: #fafbff;
}

.edit-dialog-tip {
  font-size: 13px;
  color: #999;
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.edit-dialog-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.confirm-btn,
.cancel-btn {
  flex: 1;
  padding: 14px 24px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.confirm-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.confirm-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.confirm-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
  box-shadow: none;
}

.cancel-btn {
  background: white;
  color: #666;
  border: 2px solid #e0e0e0;
}

.cancel-btn:hover {
  background: #f5f5f5;
  border-color: #ccc;
  transform: translateY(-2px);
}

/* Responsive */
@media (max-width: 768px) {
  .users-grid {
    grid-template-columns: 1fr;
  }

  .stats-cards {
    grid-template-columns: 1fr;
  }

  .filter-buttons {
    flex-wrap: wrap;
  }
}
</style>
