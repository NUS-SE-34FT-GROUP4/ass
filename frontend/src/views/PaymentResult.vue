<template>
  <div class="result-page">
    <div class="result-container">
      <!-- Success -->
      <div v-if="status === 'success'" class="result-content success">
        <div class="result-icon">
          <div class="icon-circle">✓</div>
        </div>
        <h1 class="result-title">Payment Successful!</h1>
        <p class="result-message">Your order has been paid. The seller will ship it soon</p>

        <div class="result-details">
          <div class="detail-item">
            <span class="label">Order No.:</span>
            <span class="value">{{ orderId }}</span>
          </div>
          <div class="detail-item">
            <span class="label">Amount paid:</span>
            <span class="value amount">¥{{ amount }}</span>
          </div>
          <div class="detail-item">
            <span class="label">Payment method:</span>
            <span class="value">{{ paymentMethodName }}</span>
          </div>
          <div class="detail-item">
            <span class="label">Paid at:</span>
            <span class="value">{{ currentTime }}</span>
          </div>
        </div>

        <div class="result-actions">
          <button class="btn btn-primary" @click="goToOrders">
            View Order
          </button>
          <button class="btn btn-secondary" @click="goToHome">
            Back to Home
          </button>
        </div>
      </div>

      <!-- Failure -->
      <div v-else class="result-content failed">
        <div class="result-icon">
          <div class="icon-circle failed">✕</div>
        </div>
        <h1 class="result-title">Payment Failed</h1>
        <p class="result-message">{{ message || 'Something went wrong during payment. Please try again' }}</p>

        <div class="result-details">
          <div class="detail-item">
            <span class="label">Order No.:</span>
            <span class="value">{{ orderId }}</span>
          </div>
          <div class="detail-item">
            <span class="label">Reason:</span>
            <span class="value error">{{ message || 'Unknown error' }}</span>
          </div>
        </div>

        <div class="result-actions">
          <button class="btn btn-primary" @click="retryPayment">
            Pay Again
          </button>
          <button class="btn btn-secondary" @click="goToOrders">
            View Order
          </button>
          <button class="btn btn-secondary" @click="goToHome">
            Back to Home
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';

const router = useRouter();
const route = useRoute();

const orderId = ref(route.query.orderId);
const status = ref(route.query.status || 'failed');
const amount = ref(route.query.amount);
const method = ref(route.query.method);
const message = ref(route.query.message);
const currentTime = ref('');

const paymentMethods = {
  alipay: 'Alipay',
  wechat: 'WeChat Pay',
  bank: 'Bank Card',
  balance: 'Balance'
};

const paymentMethodName = computed(() => {
  return paymentMethods[method.value] || 'Unknown';
});

const goToOrders = () => {
  router.push('/order-history');
};

const goToHome = () => {
  router.push('/');
};

const retryPayment = () => {
  router.push(`/payment/${orderId.value}`);
};

onMounted(() => {
  // Format the current time
  const now = new Date();
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
});
</script>

<style scoped>
.result-page {
  min-height: 100vh;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.result-container {
  max-width: 600px;
  width: 100%;
}

.result-content {
  background: white;
  border-radius: 20px;
  padding: 60px 40px;
  text-align: center;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
}

.result-icon {
  margin-bottom: 24px;
}

.icon-circle {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: white;
  font-size: 60px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  animation: scaleIn 0.5s ease-out;
}

.icon-circle.failed {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%);
}

@keyframes scaleIn {
  from {
    transform: scale(0);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

.result-title {
  font-size: 32px;
  color: #333;
  margin-bottom: 12px;
  animation: fadeInUp 0.6s ease-out 0.2s both;
}

.result-message {
  font-size: 16px;
  color: #666;
  margin-bottom: 40px;
  animation: fadeInUp 0.6s ease-out 0.3s both;
}

@keyframes fadeInUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.result-details {
  background: #f9f9f9;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 32px;
  text-align: left;
  animation: fadeInUp 0.6s ease-out 0.4s both;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #eee;
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-item .label {
  color: #999;
  font-size: 14px;
}

.detail-item .value {
  color: #333;
  font-size: 14px;
  font-weight: 500;
}

.detail-item .value.amount {
  color: #ff4d4f;
  font-size: 18px;
  font-weight: bold;
}

.detail-item .value.error {
  color: #ff4d4f;
}

.result-actions {
  display: flex;
  gap: 16px;
  animation: fadeInUp 0.6s ease-out 0.5s both;
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

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.5);
}

.btn-secondary {
  background: white;
  color: #666;
  border: 2px solid #e8e8e8;
}

.btn-secondary:hover {
  background: #f5f5f5;
  border-color: #d9d9d9;
}
</style>

