import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/store/auth';

import HomeView from '../views/HomeView.vue';
import LoginView from '../views/LoginView.vue';
import RegisterView from '../views/RegisterView.vue';
import ForgotPasswordView from '../views/ForgotPasswordView.vue';
import ProductCreate from '../views/ProductCreate.vue';
import ProductDetail from '../views/ProductDetail.vue';
import AdminUsers from '../views/AdminUsers.vue';
import ChatView from '../views/ChatView.vue';
import AdminChatView from '../views/AdminChatView.vue';
import FavoritesView from '../views/FavoritesView.vue';
import CartView from '../views/CartView.vue';
import UserProductsView from '../views/UserProductsView.vue';
import ProductManageView from '../views/ProductManageView.vue';
import AdminReports from '../views/AdminReports.vue';
import OrderHistory from '../views/OrderHistory.vue';
import PaymentView from '../views/PaymentView.vue';
import PaymentResult from '../views/PaymentResult.vue';
import PaymentPasswordSetup from '../views/PaymentPasswordSetup.vue';
import BargainView from '../views/BargainView.vue';
import ReviewView from '../views/ReviewView.vue';

// ===== Route definitions =====
const routes = [
    {
        path: '/',
        name: 'home',
        component: HomeView,
        meta: { requiresAuth: true }, // Requires sign-in
    },
    {
        path: '/login',
        name: 'login',
        component: LoginView,
        meta: { requiresAuth: false }, // The login page needs no sign-in
    },
    {
        path: '/register',
        name: 'register',
        component: RegisterView,
        meta: { requiresAuth: false },
    },
    {
        path: '/forgot-password',
        name: 'forgot-password',
        component: ForgotPasswordView,
        meta: { requiresAuth: false },
    },
    {
        path: '/products/create',
        name: 'product-create',
        component: ProductCreate,
        meta: { requiresAuth: true },
    },
    {
        path: '/products/:id',
        name: 'product-detail',
        component: ProductDetail,
    },
    {
        path: '/admin/users',
        name: 'admin-users',
        component: AdminUsers,
        meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
        path: '/chat',
        name: 'chat',
        component: ChatView,
        meta: { requiresAuth: true },
    },
    {
        path: '/favorites',
        name: 'favorites',
        component: FavoritesView,
        meta: { requiresAuth: true },
    },
    {
        path: '/cart',
        name: 'cart',
        component: CartView,
        meta: { requiresAuth: true },
    },
    {
        path: '/admin/chat',
        name: 'admin-chat',
        component: AdminChatView,
        meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
        path: '/admin/users/:userId/products',
        name: 'user-products',
        component: UserProductsView,
        meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
        path: '/products/manage',
        name: 'product-manage',
        component: ProductManageView,
        meta: { requiresAuth: true },
    },
    {
        path: '/admin/reports',
        name: 'admin-reports',
        component: AdminReports,
        meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
        path: '/order-history',
        name: 'order-history',
        component: OrderHistory,
        meta: { requiresAuth: true },
    },
    {
        path: '/payment/:orderId',
        name: 'payment',
        component: PaymentView,
        meta: { requiresAuth: true },
    },
    {
        path: '/payment-result',
        name: 'payment-result',
        component: PaymentResult,
        meta: { requiresAuth: true },
    },
    {
        path: '/payment-password/setup',
        name: 'payment-password-setup',
        component: PaymentPasswordSetup,
        meta: { requiresAuth: true },
    },
    {
        path: '/bargain/:id',
        name: 'bargain',
        component: BargainView,
        meta: { requiresAuth: false }, // Bargain pages can be shared and viewed without signing in
    },
    {
        path: '/review/:orderId',
        name: 'review',
        component: ReviewView,
        meta: { requiresAuth: true },
    },
    {
        path: '/:pathMatch(.*)*',
        redirect: '/', // Redirect every unmatched path to the home page
    },
];

// ===== Create the router =====
const router = createRouter({
    history: createWebHistory(process.env.BASE_URL),
    routes,
});

// ===== Global route guard =====
router.beforeEach((to, from, next) => {
    const authStore = useAuthStore();

    // Requires sign-in
    if (to.meta.requiresAuth && !authStore.isLoggedIn) {
        return next('/login');
    }

    // Signed-in users cannot open login/register
    if ((to.path === '/login' || to.path === '/register') && authStore.isLoggedIn) {
        return next('/');
    }

    // Requires administrator access
    if (to.meta.requiresAdmin) {
        const role = authStore.user?.role;
        if (role !== 'ROLE_ADMIN') {
            return next('/');
        }
    }

    return next();
});

export default router;