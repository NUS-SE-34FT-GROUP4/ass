<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-content">
      <h2>Report Product</h2>
      <form @submit.prevent="submitReport">
        <div class="form-group">
          <label for="reason">Reason</label>
          <select id="reason" v-model="reason" required>
            <option value="">Choose a reason</option>
            <option value="INAPPROPRIATE_CONTENT">Inappropriate content</option>
            <option value="SCAM">Scam</option>
            <option value="SPAM">Spam</option>
            <option value="OTHER">Other</option>
          </select>
        </div>
        <div class="form-group">
          <label for="description">Details</label>
          <textarea id="description" v-model="description" rows="4"></textarea>
        </div>
        <div class="form-actions">
          <button type="button" @click="$emit('close')">Cancel</button>
          <button type="submit">Submit</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import axios from 'axios';
import { toast } from '@/services/toast';

const props = defineProps({
  productId: {
    type: Number,
    required: true,
  },
});

const emit = defineEmits(['close']);

const reason = ref('');
const description = ref('');

const submitReport = async () => {
  if (!reason.value) {
    toast('Please choose a reason', 'warning');
    return;
  }

  try {
    const response = await axios.post('/api/reports', {
      productId: props.productId,
      reason: reason.value,
      description: description.value,
    });
    toast(response.data || 'Report submitted. An administrator will review it shortly', 'success');
    emit('close');
  } catch (error) {
    console.error('Report failed:', error);
    // Show the error message returned by the backend
    const errorMessage = error.response?.data || 'Report failed, please try again';
    toast(errorMessage, 'error');
  }
};
</script>

<style scoped>
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
}

.modal-content {
  background: white;
  padding: 20px;
  border-radius: 8px;
  width: 400px;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: block;
  margin-bottom: 5px;
}

.form-group select,
.form-group textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
