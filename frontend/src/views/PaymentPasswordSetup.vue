<template>
  <div class="payment-password-setup">
    <div class="setup-container">
      <div class="setup-header">
        <h2>{{ isUpdate ? 'Change Payment Password' : 'Set Payment Password' }}</h2>
        <p class="subtitle">{{ isUpdate ? 'For your security, please enter your current password' : 'Set a 6-digit payment password to confirm payments' }}</p>
      </div>

      <div class="setup-form">
        <div class="form-group" v-if="isUpdate">
          <label>Current payment password</label>
          <input
            type="password"
            v-model="oldPassword"
            maxlength="6"
            placeholder="Enter your current payment password"
            class="password-input"
          />
        </div>

        <div class="form-group">
          <label>{{ isUpdate ? 'New payment password' : 'Payment password' }}</label>
          <input
            type="password"
            v-model="password"
            maxlength="6"
            placeholder="Enter 6 digits"
            class="password-input"
          />
        </div>

        <div class="form-group">
          <label>Confirm password</label>
          <input
            type="password"
            v-model="confirmPassword"
            maxlength="6"
            placeholder="Enter the password again"
            class="password-input"
          />
        </div>

        <div class="password-tips">
          <p>💡 Tips:</p>
          <ul>
            <li>The payment password must be 6 digits</li>
            <li>Avoid simple passwords (such as 123456)</li>
            <li>Keep your payment password safe</li>
          </ul>
        </div>

        <div class="button-group">
          <button class="btn btn-cancel" @click="goBack">Cancel</button>
          <button class="btn btn-primary" @click="submitPassword" :disabled="loading">
            {{ loading ? 'Processing...' : 'Confirm' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';
import toast from '@/utils/toast';
import Swal from 'sweetalert2';

const router = useRouter();
const isUpdate = ref(false);
const oldPassword = ref('');
const password = ref('');
const confirmPassword = ref('');
const loading = ref(false);

const checkPaymentPasswordStatus = async () => {
  try {
    const response = await axios.get('/api/users/payment-password/check');
    isUpdate.value = response.data.hasPaymentPassword;
  } catch (error) {
    console.error('Failed to check payment password status:', error);
  }
};

const submitPassword = async () => {
  // Validate the input
  if (isUpdate.value && !oldPassword.value) {
    toast.warning('Please enter your current payment password');
    return;
  }

  if (!password.value) {
    toast.warning('Please enter a payment password');
    return;
  }

  if (!/^\d{6}$/.test(password.value)) {
    toast.warning('The payment password must be 6 digits');
    return;
  }

  if (password.value !== confirmPassword.value) {
    toast.warning('The two passwords do not match');
    return;
  }

  // Weak password check
  const weakPasswords = ['123456', '000000', '111111', '222222', '333333', '444444', '555555', '666666', '777777', '888888', '999999'];
  if (weakPasswords.includes(password.value)) {
    const result = await Swal.fire({
      title: 'This password is too simple',
      text: "It is easy to guess. Use it anyway?",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Use It',
      cancelButtonText: 'Cancel'
    });

    if (!result.isConfirmed) {
      return;
    }
  }

  loading.value = true;

  try {
    if (isUpdate.value) {
      // Change the payment password
      await axios.put('/api/users/payment-password/update', {
        oldPassword: oldPassword.value,
        newPassword: password.value,
        confirmPassword: confirmPassword.value
      });
      toast.success('Payment password changed');
    } else {
      // Set the payment password
      await axios.post('/api/users/payment-password/set', {
        password: password.value,
        confirmPassword: confirmPassword.value
      });
      toast.success('Payment password set');
    }

    // Clear the inputs
    oldPassword.value = '';
    password.value = '';
    confirmPassword.value = '';

    // Check the status again to make sure the update took effect
    await checkPaymentPasswordStatus();

    // Check whether we should return to the payment page
    const returnToPaymentOrderId = sessionStorage.getItem('returnToPayment');

    // Delay the redirect so the backend state has fully synced (1 second)
    setTimeout(() => {
      if (returnToPaymentOrderId) {
        // Clear the flag
        sessionStorage.removeItem('returnToPayment');
        // Back to the payment page
        router.push(`/payment/${returnToPaymentOrderId}`);
      } else {
        // Back to the previous page
        router.back();
      }
    }, 1000);
  } catch (error) {
    console.error('Operation failed:', error);
    if (error.response?.data?.message) {
      toast.error(error.response.data.message);
    } else {
      toast.error('Operation failed, please try again');
    }
  } finally {
    loading.value = false;
  }
};

const goBack = () => {
  router.back();
};

onMounted(() => {
  checkPaymentPasswordStatus();
});
</script>

<style scoped>
.payment-password-setup {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.setup-container {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  max-width: 500px;
  width: 100%;
  padding: 40px;
}

.setup-header {
  text-align: center;
  margin-bottom: 30px;
}

.setup-header h2 {
  font-size: 28px;
  color: #333;
  margin-bottom: 10px;
}

.subtitle {
  color: #666;
  font-size: 14px;
}

.setup-form {
  margin-top: 30px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: #333;
  font-weight: 500;
}

.password-input {
  width: 100%;
  padding: 12px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 16px;
  transition: border-color 0.3s;
  letter-spacing: 4px;
}

.password-input:focus {
  outline: none;
  border-color: #667eea;
}

.password-tips {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 15px;
  margin: 20px 0;
}

.password-tips p {
  font-weight: 500;
  color: #333;
  margin-bottom: 10px;
}

.password-tips ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.password-tips li {
  color: #666;
  font-size: 14px;
  margin-bottom: 5px;
  padding-left: 20px;
  position: relative;
}

.password-tips li:before {
  content: '•';
  position: absolute;
  left: 0;
  color: #667eea;
}

.button-group {
  display: flex;
  gap: 15px;
  margin-top: 30px;
}

.btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-cancel {
  background: #f0f0f0;
  color: #666;
}

.btn-cancel:hover {
  background: #e0e0e0;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 576px) {
  .setup-container {
    padding: 30px 20px;
  }

  .button-group {
    flex-direction: column;
  }
}
</style>

