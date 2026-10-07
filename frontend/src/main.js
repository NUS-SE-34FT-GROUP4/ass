import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { createPinia } from 'pinia';
import axios from 'axios';
import { useAuthStore } from '@/store/auth';
import { toast } from '@/services/toast';

// ========== Create the Vue app ==========
const app = createApp(App);

// ========== Register Pinia and the router ==========
const pinia = createPinia();
app.use(pinia);
app.use(router);

// ========== Initialise the auth store ==========
const authStore = useAuthStore();
authStore.init(); // 🔹 Restore the session (user and token from localStorage)

// ========== Axios interceptors ==========

// Request interceptor: add the Authorization header to every request
axios.interceptors.request.use(
    (config) => {
        const token = authStore.token || localStorage.getItem('jwt_token');
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// Response interceptor: only handles 401 sign-out; components show their own errors
axios.interceptors.response.use(
    (response) => response,
    (error) => {
        const status = error?.response?.status;
        const url = error?.config?.url || '';
        const currentPath = router.currentRoute.value.path;

        // A 401 on an API request, outside the login/register/forgot-password pages
        if (status === 401 && url.startsWith('/api')) {
            // Skip public endpoints such as login, register and forgot password
            const publicPaths = ['/login', '/register', '/forgot-password'];
            const isPublicPage = publicPaths.some(path => currentPath.includes(path));
            const isPublicApi = url.includes('/auth/login') ||
                               url.includes('/auth/register') ||
                               url.includes('/auth/forgot-password') ||
                               url.includes('/auth/reset-password');

            // Only warn about an expired session on non-public pages and APIs
            if (!isPublicPage && !isPublicApi) {
                toast('❌ Your session has expired. Please sign in again', 'error');
                authStore.logout();
                router.push('/login');
            }
        }
        // ⚠️ Errors are no longer shown here; components handle them to avoid duplicate messages and leaking error codes
        return Promise.reject(error);
    }
);

// ========== Mount the app ==========
app.mount('#app');