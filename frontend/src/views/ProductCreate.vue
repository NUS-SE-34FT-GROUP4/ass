<template>
  <div class="product-create-page">
    <div class="create-header">
      <h1>📝 List a Product</h1>
      <button @click="goBack" class="btn-back">← Back</button>
    </div>

    <form @submit.prevent="handleSubmit" class="create-form">
      <div class="form-group">
        <label>Product name *</label>
        <input v-model="product.name" type="text" placeholder="Enter the product name" required />
      </div>

      <div class="form-group">
        <label>Category *</label>
        <select v-model="selectedMainCategory" @change="onMainCategoryChange" required>
          <option value="">Choose a category</option>
          <option v-for="category in mainCategories" :key="category.value" :value="category.value">
            {{ category.label }}
          </option>
        </select>
      </div>

      <div v-if="selectedMainCategory" class="form-group">
        <label>Subcategory *</label>
        <select v-model="product.category" required>
          <option value="">Choose a subcategory</option>
          <option v-for="subCat in availableSubCategories" :key="subCat.value" :value="subCat.value">
            {{ subCat.label }}
          </option>
        </select>
      </div>

      <div class="form-group">
        <label>Description *</label>
        <textarea v-model="product.description" placeholder="Describe the product in detail" rows="5" required></textarea>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>Price (¥) *</label>
          <input v-model.number="product.price" type="number" step="0.01" min="0.01" max="99999999.99" placeholder="0.00" required />
          <p class="help-text" style="font-size: 12px; color: #666; margin-top: 4px;">Maximum price: ¥99,999,999.99</p>
        </div>

        <div class="form-group">
          <label>Stock *</label>
          <input v-model.number="product.stock" type="number" min="1" placeholder="1" required />
        </div>

        <div class="form-group">
          <label>Condition *</label>
          <select v-model.number="product.conditionLevel" required>
            <option :value="10">Brand new (10/10)</option>
            <option :value="9">Like new (9/10)</option>
            <option :value="8">Good (8/10)</option>
            <option :value="7">Fair (7/10)</option>
            <option :value="6">Used (6/10)</option>
            <option :value="5">Well used (5/10)</option>
          </select>
        </div>
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>Province/Region *</label>
          <select v-model="selectedProvince" @change="onProvinceChange" required>
            <option value="">Choose a province or region</option>
            <option v-for="province in provinces" :key="province.code" :value="province.code">
              {{ province.name }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label>City *</label>
          <select v-model="product.location" required :disabled="!selectedProvince">
            <option value="">Choose a city</option>
            <option v-for="city in availableCities" :key="city.code" :value="city.name">
              {{ city.name }}
            </option>
          </select>
        </div>
      </div>

      <div class="form-group">
        <label>Images/Videos</label>
        <input type="file" @change="handleFileChange" accept="image/*,video/*" multiple class="file-input" />
        <p class="help-text">Upload several images or videos, up to 10 MB each</p>

        <div v-if="mediaPreviews.length > 0" class="media-preview-grid">
          <div v-for="(preview, index) in mediaPreviews" :key="index" class="media-preview-item">
            <img v-if="preview.type === 'image'" :src="preview.url" alt="Preview" />
            <video v-if="preview.type === 'video'" :src="preview.url" controls></video>
            <button type="button" @click="removeMedia(index)" class="remove-media-btn">×</button>
          </div>
        </div>
      </div>

      <div class="form-actions">
        <button type="button" @click="goBack" class="btn-cancel">Cancel</button>
        <button type="submit" class="btn-submit" :disabled="submitting">
          {{ submitting ? 'Publishing...' : 'Publish' }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { useRouter } from 'vue-router';
import productService from '@/api/productService';
import { provinces, cities } from '@/utils/locationData';
import { getMainCategories, getSubCategories } from '@/utils/categoryData';
import { toast } from '@/services/toast';

const router = useRouter();

const product = reactive({
  name: '',
  description: '',
  price: null,
  stock: 1,
  conditionLevel: 9,
  location: '',
  category: ''
});

const selectedProvince = ref('');
const selectedMainCategory = ref('');
const mediaFiles = ref([]);
const mediaPreviews = ref([]);
const submitting = ref(false);

// Category data
const mainCategories = getMainCategories();

const availableSubCategories = computed(() => {
  if (!selectedMainCategory.value) return [];
  return getSubCategories(selectedMainCategory.value);
});

const availableCities = computed(() => {
  if (!selectedProvince.value) return [];
  return cities[selectedProvince.value] || [];
});

const onProvinceChange = () => {
  product.location = '';
};

const onMainCategoryChange = () => {
  product.category = '';
};

const handleFileChange = (event) => {
  const files = Array.from(event.target.files);

  files.forEach(file => {
    if (file.size > 10 * 1024 * 1024) {
      toast('File size must not exceed 10 MB', 'error');
      return;
    }

    const reader = new FileReader();
    reader.onload = (e) => {
      const type = file.type.startsWith('image/') ? 'image' : 'video';
      mediaPreviews.value.push({
        type,
        url: e.target.result
      });
      mediaFiles.value.push(file);
    };
    reader.readAsDataURL(file);
  });
};

const removeMedia = (index) => {
  mediaPreviews.value.splice(index, 1);
  mediaFiles.value.splice(index, 1);
};

const handleSubmit = async () => {
  if (!product.category) {
    toast('Please choose a category', 'warning');
    return;
  }

  // Check the price range
  if (!product.price || product.price <= 0) {
    toast('Please enter a valid price', 'warning');
    return;
  }

  if (product.price > 99999999.99) {
    toast('Price must not exceed ¥99,999,999.99', 'warning');
    return;
  }

  submitting.value = true;

  try {
    const formData = new FormData();

    // Build the product JSON to match the backend API
    const productData = {
      name: product.name,
      description: product.description,
      price: parseFloat(product.price.toFixed(2)), // Keep only 2 decimal places
      stock: product.stock,
      conditionLevel: product.conditionLevel,
      location: product.location,
      category: product.category
    };

    // The backend expects a 'productData' parameter (JSON string)
    formData.append('productData', JSON.stringify(productData));

    // Add the files
    mediaFiles.value.forEach((file) => {
      formData.append('files', file);
    });

    await productService.createProduct(formData);
    toast('✅ Product published!', 'success');
    router.push('/');
  } catch (error) {
    console.error('Failed to publish product:', error);

    // Friendly error messages without exposing HTTP status codes
    const status = error?.response?.status;
    const errorData = error?.response?.data;
    let errorMsg = '';

    if (status === 400) {
      errorMsg = '❌ Please check the product details are complete and correct';
    } else if (status === 401 || status === 403) {
      errorMsg = '❌ Please sign in before publishing a product';
    } else if (status === 500) {
      errorMsg = '❌ Server error. Please try again later';
    } else if (typeof errorData === 'string' && errorData && !errorData.includes('status code')) {
      errorMsg = '❌ ' + errorData;
    } else {
      errorMsg = '❌ Failed to publish product. Please try again later';
    }

    toast(errorMsg, 'error');
  } finally {
    submitting.value = false;
  }
};

const goBack = () => {
  router.back();
};
</script>

<style scoped>
.product-create-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}

.create-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.create-header h1 {
  font-size: 28px;
  color: #333;
}

.btn-back {
  padding: 10px 20px;
  background: #6c757d;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 16px;
  transition: background 0.3s;
}

.btn-back:hover {
  background: #5a6268;
}

.create-form {
  background: white;
  padding: 30px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: #333;
}

.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 16px;
  color: #333;
  background-color: white;
  transition: border-color 0.3s;
}

.form-group select option {
  color: #333;
  background-color: white;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #007bff;
}

.form-group select:disabled {
  background-color: #e9ecef;
  color: #6c757d;
  cursor: not-allowed;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
}

.file-input {
  padding: 8px !important;
}

.help-text {
  margin-top: 5px;
  font-size: 14px;
  color: #6c757d;
}

.media-preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 15px;
  margin-top: 15px;
}

.media-preview-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid #e0e0e0;
}

.media-preview-item img,
.media-preview-item video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove-media-btn {
  position: absolute;
  top: 5px;
  right: 5px;
  width: 30px;
  height: 30px;
  background: rgba(220, 53, 69, 0.9);
  color: white;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
  transition: background 0.3s;
}

.remove-media-btn:hover {
  background: rgba(200, 35, 51, 1);
}

.form-actions {
  display: flex;
  gap: 15px;
  justify-content: flex-end;
  margin-top: 30px;
}

.btn-cancel,
.btn-submit {
  padding: 12px 30px;
  border: none;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-cancel {
  background: #6c757d;
  color: white;
}

.btn-cancel:hover {
  background: #5a6268;
}

.btn-submit {
  background: #28a745;
  color: white;
}

.btn-submit:hover:not(:disabled) {
  background: #218838;
}

.btn-submit:disabled {
  background: #94d3a2;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .create-header {
    flex-direction: column;
    gap: 15px;
  }

  .create-form {
    padding: 20px;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>

