import axios from 'axios';

const apiBase = process.env.VUE_APP_API_BASE_URL || '/api';

const apiClient = axios.create({
  baseURL: apiBase,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor: attach the JWT automatically
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('jwt_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export default {
  createProduct(formData) {
    // For FormData, drop the default Content-Type so the browser sets multipart/form-data with the boundary
    return apiClient.post('/products', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
  },
  getProductById(id) {
    return apiClient.get(`/products/${id}`);
  },
  getAllProducts(params) {
    return apiClient.get('/products', { params });
  },
  searchProducts(params) {
    return apiClient.get('/products/search', { params });
  },
};
