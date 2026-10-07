<template>
  <div class="payment-page">
    <div class="payment-container">
      <div class="payment-header">
        <h1>💳 Checkout</h1>
        <p class="order-info">Order No.: {{ orderId }}</p>
      </div>

      <!-- Order information -->
      <div class="order-summary" v-if="order">
        <h3>Order Summary</h3>

        <!-- Countdown -->
        <div class="countdown-alert" :class="{ warning: remainingMinutes < 5 }">
          <span class="countdown-icon">⏰</span>
          <span v-if="!isExpired">Please pay within <strong>{{ countdownText }}</strong></span>
          <span v-else class="expired-text">This order has expired</span>
        </div>


        <div class="summary-item">
          <span>Subtotal</span>
          <span class="amount">¥{{ order.totalAmount }}</span>
        </div>
        <div class="summary-divider"></div>
        <div class="summary-item total">
          <span>Amount due</span>
          <span class="total-amount">¥{{ order.totalAmount }}</span>
        </div>
      </div>

      <!-- Payment method -->
      <div class="payment-methods">
        <h3>Choose a Payment Method</h3>
        <div class="methods-list">
          <div
            v-for="method in paymentMethods"
            :key="method.id"
            class="method-item"
            :class="{
              active: selectedMethod === method.id,
              disabled: method.disabled
            }"
            @click="!method.disabled && selectMethod(method.id)"
          >
            <div class="method-icon">{{ method.icon }}</div>
            <div class="method-info">
              <div class="method-name">
                {{ method.name }}
                <span v-if="method.disabled" class="insufficient-label">(insufficient balance)</span>
              </div>
              <div class="method-desc" :class="{ warning: method.disabled }">
                {{ method.description }}
              </div>
            </div>
            <div class="method-radio">
              <div class="radio-dot" v-if="selectedMethod === method.id"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- Payment buttons -->
      <div class="payment-actions">
        <button class="btn btn-home" @click="goHome">🏠 Back to Home</button>
        <button class="btn btn-cancel" @click="cancelOrder">Cancel Order</button>
        <button
          class="btn btn-pay"
          @click="confirmPayment"
          :disabled="!selectedMethod || processing"
        >
          <span v-if="!processing">Pay Now ¥{{ order?.totalAmount || 0 }}</span>
          <span v-else>Processing...</span>
        </button>
      </div>

      <!-- Payment password dialog -->
      <div v-if="showPasswordDialog" class="password-overlay" @click="closePasswordDialog">
        <div class="password-dialog" @click.stop>
          <div class="dialog-header">
            <h3>Enter Your Payment Password</h3>
            <button @click="closePasswordDialog" class="close-btn">×</button>
          </div>
          <div class="dialog-body">
            <div class="payment-info">
              <div class="payment-method-icon">{{ currentMethodIcon }}</div>
              <div class="payment-amount">¥{{ order?.totalAmount || 0 }}</div>
            </div>
            <div class="password-input-container">
              <input
                v-for="i in 6"
                :key="i"
                type="password"
                maxlength="1"
                class="password-digit"
                :ref="el => passwordInputs[i - 1] = el"
                v-model="passwordDigits[i - 1]"
                @input="handlePasswordInput(i - 1)"
                @keydown="handleKeyDown($event, i - 1)"
              />
            </div>
            <p class="password-tip">For the security of your funds, enter your 6-digit payment password</p>
          </div>
          <div class="dialog-actions">
            <button class="btn btn-cancel" @click="closePasswordDialog">Cancel</button>
            <button
              class="btn btn-confirm"
              @click="submitPayment"
              :disabled="password.length !== 6 || processing"
            >
              Confirm Payment
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import axios from 'axios';
import toast from '@/utils/toast';
import Swal from 'sweetalert2';

const router = useRouter();
const route = useRoute();
const orderId = ref(route.params.orderId || route.query.orderId);
const order = ref(null);
const selectedMethod = ref(null);
const processing = ref(false);
const showPasswordDialog = ref(false);
const passwordDigits = ref(['', '', '', '', '', '']);
const passwordInputs = ref([]);
const remainingTime = ref(0);
const isExpired = ref(false);
const hasPaymentPassword = ref(null); // Cached payment password status
const userBalance = ref(0); // User balance
let countdownTimer = null;

const paymentMethods = computed(() => [
  {
    id: 'alipay',
    name: 'Alipay',
    icon: '💙',
    description: 'Fast checkout with Alipay'
  },
  {
    id: 'wechat',
    name: 'WeChat Pay',
    icon: '💚',
    description: 'Secure payment with WeChat'
  },
  {
    id: 'bank',
    name: 'Bank Card',
    icon: '💳',
    description: 'Debit and credit cards accepted'
  },
  {
    id: 'balance',
    name: 'Balance',
    icon: '💰',
    description: `Current balance: ¥${userBalance.value.toFixed(2)}`,
    disabled: userBalance.value < (order.value?.totalAmount || 0)
  }
]);

const password = computed(() => passwordDigits.value.join(''));

const currentMethodIcon = computed(() => {
  const method = paymentMethods.value.find(m => m.id === selectedMethod.value);
  return method ? method.icon : '💳';
});

const fetchOrderDetails = async () => {
  try {
    const response = await axios.get(`/api/orders/${orderId.value}`);
    order.value = response.data;

    // Check the order status
    if (order.value.status === 'EXPIRED' || order.value.status === 'CANCELED') {
      toast.warning(`This order has ${order.value.status === 'EXPIRED' ? 'expired' : 'been cancelled'}`);
      router.push('/order-history');
      return;
    }

    if (order.value.status !== 'PENDING') {
      toast.warning('This order cannot be paid');
      router.push('/order-history');
      return;
    }

    // Check whether the order has already expired (client-side)
    if (order.value.expireTime) {
      const now = new Date().getTime();
      const expireTime = new Date(order.value.expireTime).getTime();
      if (now >= expireTime) {
        isExpired.value = true;
        toast.warning('The order has expired and the items are back in your cart');
        router.push({
          path: '/payment-result',
          query: {
            orderId: orderId.value,
            status: 'failed',
            message: 'The order was closed automatically because payment timed out'
          }
        });
        return;
      }
    }

    // Start the countdown
    startCountdown();

    // Load the user's balance
    await fetchUserBalance();
  } catch (error) {
    console.error('Failed to load order details:', error);
    toast.error('Failed to load order details');
    router.push('/order-history');
  }
};

// Get the user's balance
const fetchUserBalance = async () => {
  try {
    const response = await axios.get('/api/users/me');
    userBalance.value = response.data.balance || 0;
  } catch (error) {
    console.error('Failed to load balance:', error);
    userBalance.value = 0;
  }
};

const selectMethod = (methodId) => {
  selectedMethod.value = methodId;
};

const confirmPayment = async () => {
  if (!selectedMethod.value) {
    toast.warning('Please choose a payment method');
    return;
  }

  // Check whether the user has set a payment password (checked every time to avoid stale cache)
  try {
    const response = await axios.get('/api/users/payment-password/check', {
      headers: {
        'Cache-Control': 'no-cache',
        'Pragma': 'no-cache'
      }
    });
    hasPaymentPassword.value = response.data.hasPaymentPassword;

    if (!hasPaymentPassword.value) {
      const result = await Swal.fire({
        title: 'You have not set a payment password',
        text: "Set one now?",
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#d33',
        confirmButtonText: 'Set It Now',
        cancelButtonText: 'Cancel'
      });

      if (result.isConfirmed) {
        // Remember the current route so we can return after setup
        sessionStorage.setItem('returnToPayment', orderId.value);
        router.push('/payment-password/setup');
      }
      return;
    }
  } catch (error) {
    console.error('Failed to check payment password:', error);
    toast.error('Failed to check payment password status, please try again');
    return;
  }

  showPasswordDialog.value = true;
  // Focus the first input automatically
  setTimeout(() => {
    if (passwordInputs.value[0]) {
      passwordInputs.value[0].focus();
    }
  }, 100);
};

const closePasswordDialog = () => {
  showPasswordDialog.value = false;
  passwordDigits.value = ['', '', '', '', '', ''];
};

const handlePasswordInput = (index) => {
  // After a digit is entered, move to the next input
  if (passwordDigits.value[index] && index < 5) {
    passwordInputs.value[index + 1]?.focus();
  }
};

const handleKeyDown = (event, index) => {
  // On backspace, move to the previous input
  if (event.key === 'Backspace' && !passwordDigits.value[index] && index > 0) {
    passwordInputs.value[index - 1]?.focus();
  }
};

const remainingMinutes = computed(() => Math.floor(remainingTime.value / 60));

const countdownText = computed(() => {
  if (remainingTime.value <= 0) return '0m 0s';
  const minutes = Math.floor(remainingTime.value / 60);
  const seconds = remainingTime.value % 60;
  return `${minutes}m ${seconds}s`;
});

const startCountdown = () => {
  if (!order.value || !order.value.expireTime) return;

  const updateCountdown = () => {
    const now = new Date().getTime();
    const expireTime = new Date(order.value.expireTime).getTime();
    const diff = Math.floor((expireTime - now) / 1000);

    if (diff <= 0) {
      remainingTime.value = 0;
      isExpired.value = true;
      clearInterval(countdownTimer);

      // The order has expired; go to the failure page
      toast.warning('The order has expired and the items are back in your cart');
      router.push({
        path: '/payment-result',
        query: {
          orderId: orderId.value,
          status: 'failed',
          message: 'The order was closed automatically because payment timed out'
        }
      });
    } else {
      remainingTime.value = diff;
    }
  };

  updateCountdown();
  countdownTimer = setInterval(updateCountdown, 1000);
};

const submitPayment = async () => {
  if (password.value.length !== 6) {
    toast.warning('Please enter the full payment password');
    return;
  }

  processing.value = true;

  try {
    // Simulate payment processing delay
    await new Promise(resolve => setTimeout(resolve, 1500));

    // Call the payment API
    await axios.post(`/api/orders/${orderId.value}/pay`, {
      paymentMethod: selectedMethod.value,
      password: password.value
    });

    // Payment succeeded; go to the success page
    closePasswordDialog();
    router.push({
      path: '/payment-result',
      query: {
        orderId: orderId.value,
        status: 'success',
        amount: order.value.totalAmount,
        method: selectedMethod.value
      }
    });
  } catch (error) {
    console.error('Payment failed:', error);
    // Payment failed; go to the failure page
    router.push({
      path: '/payment-result',
      query: {
        orderId: orderId.value,
        status: 'failed',
        message: error.response?.data?.message || 'Payment failed, please try again'
      }
    });
  } finally {
    processing.value = false;
    closePasswordDialog();
  }
};

const goHome = () => {
  router.push('/');
};

const cancelOrder = async () => {
  const result = await Swal.fire({
    title: 'Cancel this order?',
    text: "The order will be closed and the items returned to your cart.",
    icon: 'warning',
    showCancelButton: true,
    confirmButtonColor: '#d33',
    cancelButtonColor: '#3085d6',
    confirmButtonText: 'Cancel Order',
    cancelButtonText: 'Keep Order'
  });

  if (!result.isConfirmed) {
    return;
  }

  try {
    await axios.post(`/api/orders/${orderId.value}/cancel`);
    toast.success('Order cancelled');
    router.push('/');
  } catch (error) {
    console.error('Failed to cancel order:', error);
    toast.error('Failed to cancel order');
  }
};

onMounted(() => {
  if (!orderId.value) {
    toast.error('Invalid order information');
    router.push('/order-history');
    return;
  }
  fetchOrderDetails();
});

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
  }
});
</script>

<style scoped>
.payment-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 40px 20px;
}

.payment-container {
  max-width: 800px;
  margin: 0 auto;
}

.payment-header {
  text-align: center;
  color: white;
  margin-bottom: 30px;
}

.payment-header h1 {
  font-size: 36px;
  margin-bottom: 10px;
}

.order-info {
  font-size: 14px;
  opacity: 0.9;
}

.order-summary {
  background: white;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.order-summary h3 {
  font-size: 18px;
  margin-bottom: 16px;
  color: #333;
}

.countdown-alert {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: white;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.countdown-alert.warning {
  background: linear-gradient(135deg, #ff6b6b 0%, #ee5a6f 100%);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.02);
  }
}

.countdown-icon {
  font-size: 18px;
}

.expired-text {
  font-weight: bold;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  font-size: 15px;
}

.summary-item .amount {
  color: #666;
}

.summary-item.total {
  font-size: 18px;
  font-weight: bold;
}

.summary-item.total .total-amount {
  color: #ff4d4f;
  font-size: 24px;
}

.summary-divider {
  height: 1px;
  background: #eee;
  margin: 12px 0;
}

.payment-methods {
  background: white;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.payment-methods h3 {
  font-size: 18px;
  margin-bottom: 16px;
  color: #333;
}

.methods-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.method-item {
  display: flex;
  align-items: center;
  padding: 16px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.method-item:hover {
  border-color: #667eea;
  background: #f8f9ff;
}

.method-item.active {
  border-color: #667eea;
  background: #f8f9ff;
}

.method-item.disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background: #f5f5f5;
}

.method-item.disabled:hover {
  border-color: #e8e8e8;
  background: #f5f5f5;
}

.method-icon {
  font-size: 32px;
  margin-right: 16px;
}

.method-info {
  flex: 1;
}

.method-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.insufficient-label {
  color: #ff4d4f;
  font-size: 12px;
  font-weight: normal;
  margin-left: 8px;
}

.method-desc {
  font-size: 13px;
  color: #999;
}

.method-desc.warning {
  color: #ff4d4f;
  font-weight: 500;
}

.method-radio {
  width: 20px;
  height: 20px;
  border: 2px solid #ddd;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
}

.method-item.active .method-radio {
  border-color: #667eea;
}

.radio-dot {
  width: 10px;
  height: 10px;
  background: #667eea;
  border-radius: 50%;
}

.payment-actions {
  display: flex;
  gap: 16px;
}

.btn {
  flex: 1;
  padding: 16px;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-home {
  background: linear-gradient(135deg, #36d1dc 0%, #5b86e5 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(54, 209, 220, 0.4);
}

.btn-home:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(54, 209, 220, 0.5);
}

.btn-cancel {
  background: white;
  color: #666;
  border: 2px solid #e8e8e8;
}

.btn-cancel:hover {
  background: #f5f5f5;
}

.btn-pay {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-pay:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.5);
}

.btn-pay:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Payment password dialog */
.password-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.password-dialog {
  background: white;
  border-radius: 20px;
  width: 90%;
  max-width: 400px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid #eee;
}

.dialog-header h3 {
  font-size: 18px;
  color: #333;
}

.close-btn {
  background: none;
  border: none;
  font-size: 28px;
  color: #999;
  cursor: pointer;
  line-height: 1;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.3s;
}

.close-btn:hover {
  background: #f5f5f5;
  color: #666;
}

.dialog-body {
  padding: 32px 24px;
}

.payment-info {
  text-align: center;
  margin-bottom: 32px;
}

.payment-method-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.payment-amount {
  font-size: 32px;
  font-weight: bold;
  color: #ff4d4f;
}

.password-input-container {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 16px;
}

.password-digit {
  width: 48px;
  height: 56px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  text-align: center;
  font-size: 24px;
  font-weight: bold;
  transition: all 0.3s;
}

.password-digit:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.password-tip {
  text-align: center;
  font-size: 13px;
  color: #999;
}

.dialog-actions {
  display: flex;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #eee;
}

.dialog-actions .btn {
  flex: 1;
  padding: 12px;
}

.btn-confirm {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-confirm:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>

