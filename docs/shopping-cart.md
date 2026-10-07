# Shopping Cart

## Overview

The shopping cart is a temporary holding area where users collect items they are interested in, then manage and check them out together. Users can add items from several sellers, review them in one place, adjust quantities, and finally create orders and pay.

### Core features
- **Add to cart**: on the product page, the user clicks "Add to Cart" to add the current item.
- **View the cart**: the user opens the cart page from the floating cart button or the navigation to see every item added.
- **Change quantities**: on the cart page, the user can increase or decrease the quantity to buy.
- **Remove items**: the user can remove one or several items from the cart.
- **Bulk checkout**: the user selects some or all items in the cart and checks them out together, creating one or more orders.
- **Badge**: the floating cart button shows the number of distinct items in the cart in real time.

## Database schema

### cart_item table
```sql
CREATE TABLE `cart_item` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Cart item ID',
  `user_id` int NOT NULL COMMENT 'User ID',
  `product_id` int NOT NULL COMMENT 'Product ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT 'Quantity',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `cart_item_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `cart_item_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
);
```
*Note: to keep entries unique, a unique index is usually created on `(user_id, product_id)` so the same product cannot be inserted twice. If it already exists, its `quantity` should be updated instead.*

## Backend API

#### 1. Add a product to the cart
`POST /api/cart/items`
- **Authorization**: `Bearer {token}`
- **Request Body**: `{ "productId": 123, "quantity": 1 }`
- **Response**: a success or failure message and the updated total number of items in the cart.

#### 2. Get the user's cart
`GET /api/cart/items`
- **Authorization**: `Bearer {token}`
- **Response**: a list with the full details of every item in the user's cart.

#### 3. Update an item's quantity
`PUT /api/cart/items/{cartItemId}`
- **Authorization**: `Bearer {token}`
- **Request Body**: `{ "quantity": 3 }`
- **Response**: the updated cart item.

#### 4. Remove an item from the cart
`DELETE /api/cart/items/{cartItemId}`
- **Authorization**: `Bearer {token}`
- **Response**: a success or failure message.

#### 5. Remove several items from the cart
`DELETE /api/cart/items`
- **Authorization**: `Bearer {token}`
- **Request Body**: `{ "cartItemIds": [1, 2, 3] }`
- **Response**: a success or failure message.

## Frontend views and components

- **CartView.vue**: the main cart page.
  - Route: `/cart`
  - Features:
    - Lists every item in the cart, grouped by seller.
    - Checkboxes to select items to check out or remove.
    - Quantity controls (`+/-` buttons).
    - Shows the total for the selected items.
    - "Checkout" and "Remove" buttons.

- **FloatingCartButton.vue**: the global floating cart button.
  - Shown on most pages (usually registered globally in `App.vue`).
  - Shows a cart icon and a badge with the number of distinct items in the cart.
  - Clicking it navigates to `/cart`.

## Flow

1. The user clicks "Add to Cart" on `ProductDetail.vue`.
2. The frontend triggers an action (such as `addToCart` in Vuex) that calls `POST /api/cart/items`.
3. The backend `CartService.java` handles the request:
   - Checks whether the product is already in the user's cart.
   - If it is, increases its `quantity`.
   - If not, inserts a new row into `cart_item`.
4. When the API returns success, the frontend updates the UI, for example showing an "Added" message and updating the badge on `FloatingCartButton.vue`.
5. The user opens `/cart` (`CartView.vue`) from the floating button or another entry point.
6. On load, `CartView.vue` calls `GET /api/cart/items` to fetch every item in the cart and renders the list.
7. On the cart page the user changes quantities, selects items or removes them; each action calls the matching backend API to keep the database in sync.
8. When the user clicks "Checkout", the frontend passes the selected `cartItemIds` to the order page (`PaymentView.vue`) and navigates there.

## Implementation

### Backend
- **CartItem.java**: the cart item entity.
- **CartItemMapper.java**: the MyBatis mapper for database access.
- **CartService.java**: the core business logic for adding, querying, updating and removing cart items.
- **CartController.java**: the RESTful API exposed to the frontend.

### Frontend
- **Vuex store (for example a `cart.js` module)**: Vuex can optionally manage cart state such as `cartItemCount`.
  - `fetchCartItemCount` action: called on app start or after login to fetch the cart count for the floating button badge.
  - `addToCart` action: called when adding an item; increases `cartItemCount` when the backend call succeeds.
- **State management**: the cart page (`CartView.vue`) usually keeps its own state (item list, selection, total) in `data` or `ref` (Composition API), since that state is local to the page.
- **Grouping by seller**: in `CartView.vue`, the flat item list is turned into a tree grouped by `sellerId` or `sellerName` with a computed property or method, to make it easy to render in the template.
