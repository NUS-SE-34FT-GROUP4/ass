# Product Listing and Management

## Overview

The system manages the whole product lifecycle, from listing a new product to editing, listing/delisting and deleting it. It supports rich-text descriptions and multi-image upload, with static assets stored in MinIO object storage.

### Core features
- **List a product**: users fill in a form with the product name, description, price, category, campus and so on.
- **Image upload**: several product images can be uploaded; each goes to the MinIO server, which returns a URL.
- **Rich-text description**: the description supports rich text, so users can style it and insert images.
- **Product management**: users manage their own products from their profile, including editing details and listing or delisting them.
- **Product details**: anyone can view a product's full details, including all images, description, price and seller information.
- **Search and categories**: the home page supports keyword search and filtering by category and campus.

## Database schema

### product table
```sql
CREATE TABLE `product` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Product ID',
  `user_id` int NOT NULL COMMENT 'User ID',
  `title` varchar(100) NOT NULL COMMENT 'Product title',
  `description` text COMMENT 'Product description',
  `price` decimal(10,2) NOT NULL COMMENT 'Price',
  `category_id` int DEFAULT NULL COMMENT 'Category ID',
  `campus_id` int DEFAULT NULL COMMENT 'Campus ID',
  `status` varchar(20) DEFAULT 'AVAILABLE' COMMENT 'Product status (AVAILABLE, SOLD, REMOVED)',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `product_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
);
```

### product_media table
```sql
CREATE TABLE `product_media` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Media ID',
  `product_id` int NOT NULL COMMENT 'Product ID',
  `media_url` varchar(255) NOT NULL COMMENT 'Media file URL',
  `media_type` varchar(20) DEFAULT 'IMAGE' COMMENT 'Media type (IMAGE, VIDEO)',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `product_media_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
);
```

## Backend API

#### 1. List a new product
`POST /api/products`
- **Authorization**: `Bearer {token}`
- **Request Body**: a JSON object with the product details, such as `{ "title": "...", "price": 99.9, ... }`.
- **Response**: the created product.

#### 2. Upload a product image
`POST /api/products/upload`
- **Authorization**: `Bearer {token}`
- **Request**: a `multipart/form-data` file upload.
- **Response**: `{ "success": true, "url": "minio-image-url" }`.

#### 3. List all products (with paging and filters)
`GET /api/products`
- **Query Params**: `page`, `size`, `sort`, `categoryId`, `campusId`, `keyword`.
- **Response**: a page of products.

#### 4. Get a single product
`GET /api/products/{productId}`
- **Response**: the full product with the given ID, including seller details and media files.

#### 5. Update a product
`PUT /api/products/{productId}`
- **Authorization**: `Bearer {token}`
- **Request Body**: the product fields to update.
- **Response**: the updated product.

#### 6. Delete a product
`DELETE /api/products/{productId}`
- **Authorization**: `Bearer {token}`
- **Response**: a success or failure message.

#### 7. Get a user's products
`GET /api/users/{userId}/products`
- **Response**: every product the given user has listed.

## Frontend views and components

- **ProductCreate.vue**: the page for listing and editing a product.
  - Routes: `/product/create` (list), `/product/edit/:id` (edit).
  - Includes a file upload component and a rich-text editor.

- **ProductManageView.vue**: the user's product management page.
  - Route: `/manage-products`.
  - Lists the user's products with actions to edit, list/delist and delete.

- **ProductDetail.vue**: the product page.
  - Route: `/product/:id`.
  - Shows everything about the product, including an image carousel, rich-text content, price and seller credit.

- **HomeView.vue**: the home page.
  - Route: `/`.
  - Shows every product "on sale" in a waterfall layout, with search and category filters.

## Implementation

### File upload flow (MinIO)
1. **Frontend**: the user chooses image files on `ProductCreate.vue`.
2. **Frontend**: calls `POST /api/products/upload`, sending the images as `multipart/form-data`.
3. **Backend (ProductController.java)**: receives the file and calls `MinioService.java`.
4. **Backend (MinioService.java)**:
   - Generates a unique file name (usually a UUID).
   - Uploads the file stream to the bucket with the MinIO Java client's `putObject` method.
   - Returns the file's public URL.
5. **Frontend**: adds the URL to the product's media list and shows a preview.
6. **Frontend**: when the user submits the form, the list of image URLs is sent with the product details to `POST /api/products`.
7. **Backend (ProductService.java)**:
   - Creates a row in the `product` table first.
   - Then creates one row in `product_media` for each media URL.
   - Everything runs in a single database transaction to keep the data consistent.

### Rich-text editor
- The frontend uses a third-party Vue rich-text editor component (such as `vue-quill-editor`).
- The editor produces an HTML string, which the backend stores as-is in the `description` column of `product`.
- `ProductDetail.vue` renders that HTML with `v-html` to show the formatted text.
