import { defineStore } from 'pinia';
import axios from 'axios';

const API_BASE = process.env.VUE_APP_API_BASE_URL || '/api';
const AUTH_API = `${API_BASE}/auth`;
const CHAT_API = `${API_BASE}/chat`;


export const useAuthStore = defineStore('auth', {
    state: () => ({
        token: localStorage.getItem('jwt_token') || null,
        user: JSON.parse(localStorage.getItem('user')) || null,
        unreadCount: 0,
        conversations: [],
    }),

    getters: {
        isLoggedIn: (state) => !!state.token,
        unreadCount: (state) => state.unreadCount,
        conversations: (state) => state.conversations,
    },

    actions: {
        /**
         * Sign in
         * @param {Object} credentials - { username, password, captchaId, captchaCode, isAdmin }
         */
        async login(credentials) {
            try {
                const { data } = await axios.post(`${AUTH_API}/login`, {
                    username: credentials.username,
                    password: credentials.password,
                    isAdmin: credentials.isAdmin,
                    captchaId: credentials.captchaId,
                    captchaCode: credentials.captchaCode
                });
                const { token, username, role, userId, displayName, avatarUrl, email } = data;

                // Update state with the full user record
                this.token = token;
                this.user = {
                    id: userId,
                    username,
                    role,
                    displayName,
                    avatarUrl,
                    email
                };

                // Persist
                localStorage.setItem('jwt_token', token);
                localStorage.setItem('user', JSON.stringify(this.user));

                // Set the default Axios Authorization header
                axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;

                // After signing in, load unread messages, conversations and credit level
                await this.fetchUnreadCount();
                await this.fetchAllConversations();
                await this.fetchCreditScore();

            } catch (error) {
                // Clear state when sign-in fails
                this.logout();
                throw error;
            }
        },

        /**
         * Register
         * @param {Object} credentials - { username, password, email, captchaId, captchaCode }
         */
        async register(credentials) {
            return axios.post(`${AUTH_API}/register`, {
                username: credentials.username,
                password: credentials.password,
                email: credentials.email,
                captchaId: credentials.captchaId,
                captchaCode: credentials.captchaCode
            });
        },

        /**
         * Sign out
         */
        logout() {
            this.token = null;
            this.user = null;
            this.unreadCount = 0;
            this.conversations = [];

            localStorage.removeItem('jwt_token');
            localStorage.removeItem('user');

            delete axios.defaults.headers.common['Authorization'];
        },

        /**
         * Restore the session (called after a page refresh)
         */
        init() {
            const savedToken = localStorage.getItem('jwt_token');
            const savedUser = localStorage.getItem('user');

            if (savedToken && savedUser) {
                this.token = savedToken;
                this.user = JSON.parse(savedUser);
                axios.defaults.headers.common['Authorization'] = `Bearer ${savedToken}`;
                // Also load the latest data on page load
                this.fetchUnreadCount();
                this.fetchCreditScore();
                this.fetchAllConversations();
            }
        },

        /**
         * Update user details (avatar, displayName and so on)
         */
        updateUser(userData) {
            if (this.user) {
                // Merge the updated user details
                this.user = { ...this.user, ...userData };
                localStorage.setItem('user', JSON.stringify(this.user));
            }
        },

        /**
         * Get the total unread count
         */
        async fetchUnreadCount() {
            if (!this.isLoggedIn) return;
            try {
                const { data } = await axios.get(`${CHAT_API}/unread-count`);
                this.unreadCount = data;
            } catch (error) {
                console.error('Failed to load unread count:', error);
                this.unreadCount = 0;
            }
        },

        /**
         * Get all conversations
         */
        async fetchAllConversations() {
            if (!this.isLoggedIn) return;
            try {
                const { data } = await axios.get(`${CHAT_API}/conversations`);
                this.conversations = data;
            } catch (error) {
                console.error('Failed to load conversations:', error);
                this.conversations = [];
            }
        },
        /**
         * Get the user's credit level
         */
        async fetchCreditScore() {
            if (!this.isLoggedIn || !this.user?.id) return;
            try {
                const { data } = await axios.get(`${API_BASE}/credit-score/${this.user.id}`);
                if (data.success && data.data) {
                    // Update the user with the credit level
                    this.user.creditLevel = data.data.level;
                    this.user.creditLevelName = data.data.levelName;
                    this.user.totalScore = data.data.totalScore;
                    localStorage.setItem('user', JSON.stringify(this.user));
                }
            } catch (error) {
                console.error('Failed to load credit level:', error);
                // Fall back to defaults on failure
                if (this.user) {
                    this.user.creditLevel = 1;
                    this.user.creditLevelName = 'Newcomer';
                }
            }
        },

        /**
         * Update or add a single conversation
         * @param {Object} conversation - the conversation to update or add
         */
        updateOrAddConversation(conversation) {
            const index = this.conversations.findIndex(c => c.userId === conversation.userId);
            if (index !== -1) {
                // Update the existing conversation
                this.conversations[index] = { ...this.conversations[index], ...conversation };
            } else {
                // Add a new conversation
                this.conversations.unshift(conversation);
            }
        },

        /**
         * Increase the unread count
         * @param {number} [count=1] - how much to add
         */
        incrementUnreadCount(count = 1) {
            this.unreadCount += count;
        },
    },
});