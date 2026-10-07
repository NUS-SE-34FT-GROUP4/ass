<template>
  <div class="bargain-container">
    <div v-if="loading" class="loading-container">
      <div class="loading-spinner"></div>
      <p>Loading...</p>
    </div>

    <div v-else-if="bargainActivity" class="bargain-content">
      <!-- Back button -->
      <button class="btn-back" @click="goBack">← Back</button>

      <!-- Product card -->
      <div class="product-card">
        <img
          v-if="product?.media?.[0]"
          :src="product.media[0].url"
          :alt="product.name"
          class="product-image"
        />
        <div class="product-placeholder" v-else>📷</div>
        <div class="product-info">
          <h2>{{ product?.name }}</h2>
          <div class="original-price">Original price: ¥{{ bargainActivity.originalPrice }}</div>
        </div>
      </div>

      <!-- Bargain status card -->
      <div class="bargain-status-card">
        <div class="status-header">
          <h1 v-if="bargainActivity.status === 'SUCCESS'" class="status-success">
            🎉 Bargain successful!
          </h1>
          <h1 v-else-if="bargainActivity.status === 'EXPIRED'" class="status-expired">
            ⏰ Bargain expired
          </h1>
          <h1 v-else-if="bargainActivity.status === 'FAILED'" class="status-failed">
            😢 Bargain failed
          </h1>
          <h1 v-else-if="bargainActivity.status === 'COMPLETED'" class="status-completed">
            ✅ Purchased
          </h1>
          <h1 v-else class="status-ongoing">🔪 Bargain in progress</h1>
        </div>

        <!-- Reason for failure -->
        <div v-if="bargainActivity.status === 'FAILED'" class="failure-reason">
          <p>😢 Sorry, this product has been bought by someone else or delisted</p>
        </div>

        <!-- Price display -->
        <div class="price-section">
          <div class="current-price-label">Current price</div>
          <div class="current-price">¥{{ bargainActivity.currentPrice }}</div>
          <div class="target-price">Target price: ¥{{ bargainActivity.targetPrice }}</div>
        </div>

        <!-- Progress bar -->
        <div class="progress-section">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progressPercentage + '%' }"></div>
          </div>
          <div class="progress-text">
            {{ progressPercentage.toFixed(1) }}% cut
            <span class="remaining">¥{{ remainingAmount }} to go</span>
          </div>
        </div>

        <!-- Countdown -->
        <div v-if="bargainActivity.status === 'ACTIVE'" class="countdown">
          <span class="countdown-icon">⏰</span>
          Time left: {{ countdown }}
          <div class="countdown-note">Bargains last 24 hours</div>
        </div>

        <!-- Action buttons -->
        <div class="action-section">
          <!-- Bargain successful -->
          <button
            v-if="bargainActivity.status === 'SUCCESS'"
            class="btn-buy-success"
            @click="buyAtBargainPrice"
          >
            💰 Buy Now (¥{{ bargainActivity.currentPrice }})
          </button>

          <!-- Bargain in progress -->
          <template v-else-if="bargainActivity.status === 'ACTIVE'">
            <!-- The person who started the bargain -->
            <div v-if="isOwner" class="owner-actions">
              <button class="btn-share" @click="shareBargain">
                📤 Share with Friends
              </button>
              <button class="btn-abandon" @click="abandonAndBuy">
                💳 Stop Bargaining and Buy (¥{{ bargainActivity.currentPrice }})
              </button>
            </div>
            <!-- A helper -->
            <div v-else class="helper-actions">
              <button
                v-if="!hasHelped"
                class="btn-help"
                @click="helpBargain"
                :disabled="helping"
              >
                {{ helping ? 'Cutting...' : '🎁 Help Cut the Price' }}
              </button>
              <div v-else class="already-helped">
                ✅ You have already helped
              </div>
            </div>
          </template>

          <!-- Expired -->
          <button
            v-else-if="bargainActivity.status === 'EXPIRED'"
            class="btn-expired"
            @click="goToProduct"
          >
            View Product
          </button>

          <!-- Bargain failed (product bought by someone else or delisted) -->
          <button
            v-else-if="bargainActivity.status === 'FAILED'"
            class="btn-failed"
            @click="goBack"
          >
            Back to Home
          </button>

          <!-- Purchase complete -->
          <button
            v-else-if="bargainActivity.status === 'COMPLETED'"
            class="btn-completed"
            @click="goBack"
          >
            Back to Home
          </button>
        </div>
      </div>

      <!-- Helper list -->
      <div class="help-list-card">
        <h3>💪 Helpers ({{ helpList.length }})</h3>
        <div v-if="helpList.length === 0" class="empty-help">
          No helpers yet. Invite your friends!
        </div>
        <div v-else class="help-list">
          <div
            v-for="help in helpList"
            :key="help.id"
            class="help-item"
          >
            <div class="helper-avatar">{{ help.helperName.charAt(0) }}</div>
            <div class="helper-info">
              <div class="helper-name">{{ help.helperName }}</div>
              <div class="helper-time">{{ formatTime(help.createdAt) }}</div>
            </div>
            <div class="cut-amount">-¥{{ help.cutAmount }}</div>
          </div>
        </div>
      </div>

      <!-- Share dialog -->
      <div v-if="showShareModal" class="modal-overlay" @click="showShareModal = false">
        <div class="modal-content" @click.stop>
          <h3>Share Bargain Link</h3>
          <div class="share-link-container">
            <input
              ref="shareLinkInput"
              v-model="shareLinkUrl"
              readonly
              class="share-link-input"
            />
            <button class="btn-copy" @click="copyLink">
              {{ copied ? '✅ Copied' : '📋 Copy' }}
            </button>
          </div>
          <p class="share-tip">Share the link with friends so they can help you cut the price!</p>
          <button class="btn-close" @click="showShareModal = false">Close</button>
        </div>
      </div>
    </div>

    <div v-else class="error-container">
      <h2>😢 Bargain not found</h2>
      <button class="btn-back" @click="goBack">Back to Home</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import axios from 'axios';
import { toast } from '@/services/toast';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const loading = ref(true);
const bargainActivity = ref(null);
const product = ref(null);
const helpList = ref([]);
const countdown = ref('');
const helping = ref(false);
const showShareModal = ref(false);
const shareLinkUrl = ref('');
const copied = ref(false);
const shareLinkInput = ref(null);

let countdownInterval = null;

// Whether the current user started this bargain
const isOwner = computed(() => {
  if (!authStore.user || !bargainActivity.value) return false;
  return authStore.user.id === bargainActivity.value.userId;
});

// Whether the current user has already helped
const hasHelped = computed(() => {
  if (!authStore.user || !helpList.value) return false;
  return helpList.value.some(help => help.helperId === authStore.user.id);
});

// Progress percentage
const progressPercentage = computed(() => {
  if (!bargainActivity.value) return 0;
  const total = bargainActivity.value.originalPrice - bargainActivity.value.targetPrice;
  const current = bargainActivity.value.originalPrice - bargainActivity.value.currentPrice;
  return (current / total) * 100;
});

// Amount still to cut
const remainingAmount = computed(() => {
  if (!bargainActivity.value) return 0;
  const remaining = bargainActivity.value.currentPrice - bargainActivity.value.targetPrice;
  return Math.max(0, remaining).toFixed(2);
});

// Load the bargain details
const fetchBargainActivity = async () => {
  try {
    const response = await axios.get(`/api/bargain/${route.params.id}`);
    bargainActivity.value = response.data.activity;
    helpList.value = response.data.helpList || [];

    // Load the product
    if (bargainActivity.value.productId) {
      const productResponse = await axios.get(`/api/products/${bargainActivity.value.productId}`);
      product.value = productResponse.data;
    }

    // Start the countdown
    if (bargainActivity.value.status === 'ACTIVE') {
      startCountdown();
    }
  } catch (error) {
    console.error('Failed to load bargain:', error);
    toast('Failed to load bargain', 'error');
  } finally {
    loading.value = false;
  }
};

// Start the countdown
const startCountdown = () => {
  const updateCountdown = () => {
    if (!bargainActivity.value) return;

    const now = new Date().getTime();
    const expireTime = new Date(bargainActivity.value.expireTime).getTime();
    const distance = expireTime - now;

    if (distance < 0) {
      countdown.value = 'Expired';
      bargainActivity.value.status = 'EXPIRED';
      clearInterval(countdownInterval);
      return;
    }

    const hours = Math.floor(distance / (1000 * 60 * 60));
    const minutes = Math.floor((distance % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((distance % (1000 * 60)) / 1000);

    countdown.value = `${hours}h ${minutes}m ${seconds}s`;
  };

  updateCountdown();
  countdownInterval = setInterval(updateCountdown, 1000);
};

// Help cut the price
const helpBargain = async () => {
  if (!authStore.isLoggedIn) {
    toast('Please sign in first', 'warning');
    router.push('/login');
    return;
  }

  if (isOwner.value) {
    toast('You cannot help your own bargain', 'warning');
    return;
  }

  if (hasHelped.value) {
    toast('You have already helped', 'warning');
    return;
  }

  helping.value = true;

  try {
    const response = await axios.post(`/api/bargain/help/${route.params.id}`);
    const result = response.data;

    toast(`You cut ¥${result.help.cutAmount}!`, 'success');

    // Update the data
    bargainActivity.value = result.activity;
    helpList.value.unshift(result.help);

    // Check whether the bargain succeeded
    if (result.activity.status === 'SUCCESS') {
      toast('🎉 Congratulations! The bargain succeeded!', 'success');
    }
  } catch (error) {
    console.error('Help failed:', error);
    if (error.response?.data?.message) {
      toast(error.response.data.message, 'error');
    } else {
      toast('Help failed, please try again', 'error');
    }
  } finally {
    helping.value = false;
  }
};

// Give up the bargain and buy at the current price
const abandonAndBuy = async () => {
  if (!confirm(`Stop bargaining and buy at the current price of ¥${bargainActivity.value.currentPrice}?`)) {
    return;
  }

  try {
    // Call the give-up-and-buy API
    const response = await axios.post(`/api/bargain/abandon-and-buy/${route.params.id}`);
    const order = response.data;

    toast('Order created', 'success');
    // Go to the payment page
    router.push(`/payment/${order.id}`);
  } catch (error) {
    console.error('Failed to create order:', error);
    if (error.response?.data?.message) {
      toast(error.response.data.message, 'error');
    } else {
      toast('Failed to create order, please try again', 'error');
    }
  }
};

// Buy at the successfully bargained price
const buyAtBargainPrice = async () => {
  try {
    // Create the order at the bargain price
    const response = await axios.post(`/api/bargain/buy/${route.params.id}`);
    const order = response.data;

    toast('Order created', 'success');
    router.push(`/payment/${order.id}`);
  } catch (error) {
    console.error('Failed to create order:', error);
    if (error.response?.data?.message) {
      toast(error.response.data.message, 'error');
    } else {
      toast('Failed to create order, please try again', 'error');
    }
  }
};

// Share link
const shareBargain = () => {
  shareLinkUrl.value = window.location.href;
  showShareModal.value = true;
  copied.value = false;
};

// Copy link
const copyLink = async () => {
  try {
    await navigator.clipboard.writeText(shareLinkUrl.value);
    copied.value = true;
    toast('Link copied', 'success');
    setTimeout(() => {
      copied.value = false;
    }, 2000);
  } catch (error) {
    // Fall back to the old method if the clipboard API is unavailable
    if (shareLinkInput.value) {
      shareLinkInput.value.select();
      document.execCommand('copy');
      copied.value = true;
      toast('Link copied', 'success');
      setTimeout(() => {
        copied.value = false;
      }, 2000);
    }
  }
};

// Format time
const formatTime = (time) => {
  const date = new Date(time);
  const now = new Date();
  const diff = now - date;

  if (diff < 60000) return 'Just now';
  if (diff < 3600000) return `${Math.floor(diff / 60000)} min ago`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} h ago`;

  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
};

// Go back
const goBack = () => {
  router.push('/');
};

// View product details
const goToProduct = () => {
  if (product.value) {
    router.push(`/product/${product.value.id}`);
  } else {
    router.push('/');
  }
};

onMounted(() => {
  fetchBargainActivity();
});

onUnmounted(() => {
  if (countdownInterval) {
    clearInterval(countdownInterval);
  }
});
</script>

<style scoped>
.bargain-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.btn-back {
  background: rgba(255, 255, 255, 0.2);
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 20px;
  cursor: pointer;
  font-size: 16px;
  margin-bottom: 20px;
  backdrop-filter: blur(10px);
  transition: all 0.3s ease;
}

.btn-back:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: translateX(-5px);
}

/* Product card */
.product-card {
  background: white;
  border-radius: 15px;
  padding: 20px;
  display: flex;
  gap: 20px;
  align-items: center;
  margin-bottom: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
}

.product-image {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 10px;
}

.product-placeholder {
  width: 100px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f0f0;
  border-radius: 10px;
  font-size: 40px;
}

.product-info h2 {
  margin: 0 0 10px 0;
  font-size: 20px;
  color: #333;
}

.original-price {
  color: #999;
  text-decoration: line-through;
  font-size: 14px;
}

/* Bargain status card */
.bargain-status-card {
  background: white;
  border-radius: 15px;
  padding: 30px;
  margin-bottom: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
}

.status-header h1 {
  margin: 0 0 20px 0;
  text-align: center;
  font-size: 28px;
}

.status-success {
  color: #4CAF50;
}

.status-expired {
  color: #ff9800;
}

.status-failed {
  color: #f44336;
}

.status-completed {
  color: #2196F3;
}

.status-ongoing {
  color: #2196F3;
}

.failure-reason {
  text-align: center;
  padding: 15px;
  background: #ffebee;
  border-radius: 10px;
  margin-bottom: 20px;
  color: #c62828;
}

/* Price section */
.price-section {
  text-align: center;
  margin-bottom: 30px;
}

.current-price-label {
  color: #666;
  font-size: 14px;
  margin-bottom: 5px;
}

.current-price {
  font-size: 48px;
  font-weight: bold;
  color: #e74c3c;
  margin-bottom: 10px;
}

.target-price {
  color: #999;
  font-size: 16px;
}

/* Progress bar */
.progress-section {
  margin-bottom: 20px;
}

.progress-bar {
  height: 20px;
  background: #f0f0f0;
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 10px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #ff6b6b 0%, #4CAF50 100%);
  transition: width 0.5s ease;
  border-radius: 10px;
}

.progress-text {
  text-align: center;
  color: #666;
  font-size: 14px;
}

.remaining {
  margin-left: 10px;
  color: #e74c3c;
  font-weight: bold;
}

/* Countdown */
.countdown {
  text-align: center;
  padding: 15px;
  background: #fff3cd;
  border-radius: 10px;
  margin-bottom: 20px;
  font-size: 16px;
  color: #856404;
}

.countdown-icon {
  margin-right: 5px;
}

.countdown-note {
  font-size: 12px;
  color: #999;
  margin-top: 5px;
}

/* Action buttons */
.action-section {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.owner-actions,
.helper-actions {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.btn-buy-success,
.btn-help,
.btn-share,
.btn-abandon,
.btn-expired,
.btn-failed,
.btn-completed {
  padding: 15px 30px;
  border: none;
  border-radius: 10px;
  font-size: 18px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-buy-success {
  background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%);
  color: white;
  box-shadow: 0 4px 15px rgba(76, 175, 80, 0.4);
}

.btn-buy-success:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(76, 175, 80, 0.5);
}

.btn-help {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
  color: white;
  box-shadow: 0 4px 15px rgba(255, 107, 107, 0.4);
}

.btn-help:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 107, 107, 0.5);
}

.btn-help:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-share {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.btn-share:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
}

.btn-abandon {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  box-shadow: 0 4px 15px rgba(245, 87, 108, 0.4);
}

.btn-abandon:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(245, 87, 108, 0.5);
}

.btn-expired,
.btn-failed,
.btn-completed {
  background: #6c757d;
  color: white;
}

.btn-expired:hover,
.btn-failed:hover,
.btn-completed:hover {
  background: #5a6268;
}

.already-helped {
  text-align: center;
  padding: 15px;
  background: #d4edda;
  color: #155724;
  border-radius: 10px;
  font-size: 16px;
}

/* Helper list */
.help-list-card {
  background: white;
  border-radius: 15px;
  padding: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
}

.help-list-card h3 {
  margin: 0 0 15px 0;
  font-size: 18px;
  color: #333;
}

.empty-help {
  text-align: center;
  padding: 30px;
  color: #999;
}

.help-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.help-item {
  display: flex;
  align-items: center;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 10px;
  transition: all 0.3s ease;
}

.help-item:hover {
  background: #e9ecef;
  transform: translateX(5px);
}

.helper-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: bold;
  margin-right: 15px;
}

.helper-info {
  flex: 1;
}

.helper-name {
  font-weight: bold;
  color: #333;
  margin-bottom: 3px;
}

.helper-time {
  font-size: 12px;
  color: #999;
}

.cut-amount {
  font-size: 18px;
  font-weight: bold;
  color: #e74c3c;
}

/* Share dialog */
.modal-overlay {
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
  padding: 20px;
}

.modal-content {
  background: white;
  border-radius: 15px;
  padding: 30px;
  max-width: 500px;
  width: 100%;
}

.modal-content h3 {
  margin: 0 0 20px 0;
  font-size: 22px;
  color: #333;
  text-align: center;
}

.share-link-container {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
}

.share-link-input {
  flex: 1;
  padding: 12px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  color: #333;
}

.btn-copy {
  padding: 12px 20px;
  background: #007bff;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  white-space: nowrap;
  transition: all 0.3s ease;
}

.btn-copy:hover {
  background: #0056b3;
}

.share-tip {
  text-align: center;
  color: #666;
  font-size: 14px;
  margin-bottom: 20px;
}

.btn-close {
  width: 100%;
  padding: 12px;
  background: #6c757d;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.3s ease;
}

.btn-close:hover {
  background: #5a6268;
}

/* Loading state */
.loading-container,
.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  color: white;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 5px solid rgba(255, 255, 255, 0.3);
  border-top: 5px solid white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 20px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error-container h2 {
  color: white;
  margin-bottom: 20px;
}

/* Responsive */
@media (max-width: 768px) {
  .bargain-container {
    padding: 10px;
  }

  .product-card {
    flex-direction: column;
    text-align: center;
  }

  .current-price {
    font-size: 36px;
  }

  .btn-buy-success,
  .btn-help,
  .btn-share,
  .btn-abandon {
    font-size: 16px;
  }
}
</style>

