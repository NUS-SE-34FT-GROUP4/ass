import axios from 'axios';

const API_BASE = process.env.VUE_APP_API_BASE_URL || '/api';
const API_URL = `${API_BASE}/chat`;

const getAuthHeader = () => {
    const token = localStorage.getItem('jwt_token');
    return token ? { Authorization: `Bearer ${token}` } : {};
};

export const chatService = {
    // Get the chat history with a user
    getChatHistory(username, limit = 50) {
        return axios.get(`${API_URL}/history/${username}`, {
            params: { limit },
            headers: getAuthHeader()
        });
    },

    // Mark messages from a user as read
    markAsRead(username) {
        return axios.post(`${API_URL}/read/${username}`, {}, {
            headers: getAuthHeader()
        });
    },

    // Get the total unread count
    getUnreadCount() {
        return axios.get(`${API_URL}/unread-count`, {
            headers: getAuthHeader()
        });
    }
};
