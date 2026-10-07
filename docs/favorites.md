# Favorites

## Overview

Favorites let users save items they are interested in so they can find them again quickly later. Users can add an item to their favorites at any time and remove it at any time. It is a lightweight way to follow items, meant to bring users back and improve the shopping experience.

### Core features
- **Add a favorite**: on the product page, the user clicks the "Save" button (usually a heart icon) to add the item to their favorites.
- **Remove a favorite**: the user clicks the same button again, or removes the item from the favorites list.
- **View favorites**: the user opens their favorites page from their profile or the global floating button to see every saved item.
- **State sync**: the favorite button on the product page always shows whether the current user has saved the item.

## Database schema

### favorite table
```sql
CREATE TABLE `favorite` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Favorite ID',
  `user_id` int NOT NULL COMMENT 'User ID',
  `product_id` int NOT NULL COMMENT 'Product ID',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`) COMMENT 'A user can save each product only once',
  KEY `user_id` (`user_id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `favorite_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `favorite_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
);
```

## Backend API

#### 1. Add a product to favorites
`POST /api/favorites`
- **Authorization**: `Bearer {token}`
- **Request Body**: `{ "productId": 123 }`
- **Response**: a success or failure message.

#### 2. Remove a product from favorites
`DELETE /api/favorites/{productId}`
- **Authorization**: `Bearer {token}`
- **Response**: a success or failure message.

#### 3. Get the user's favorites
`GET /api/favorites`
- **Authorization**: `Bearer {token}`
- **Response**: a list with the details of every product the user has saved.

#### 4. Check whether a product is a favorite
`GET /api/favorites/status/{productId}`
- **Authorization**: `Bearer {token}`
- **Response**: `{ "isFavorited": true }` or `{ "isFavorited": false }`.

## Frontend views and components

- **FavoritesView.vue**: the user's favorites page.
  - Route: `/favorites`
  - Features:
    - Shows every saved item as a card or list entry.
    - Each entry shows the image, title, price and other basic details.
    - A "Remove" button, or a link to the product page when the item is clicked.

- **FloatingFavoritesButton.vue**: the global floating favorites button.
  - Shown on most pages as a shortcut to the favorites page.
  - Clicking it navigates to `/favorites`.

- **Favorite button on the product page**:
  - Usually implemented in `ProductDetail.vue`.
  - A toggle button (for example an outlined or filled icon).
  - On page load it calls `GET /api/favorites/status/{productId}` to set its initial state.

## Flow

1. After signing in, the user opens any product page (`ProductDetail.vue`).
2. On load, the page calls (in parallel) `GET /api/products/{productId}` for the product and `GET /api/favorites/status/{productId}` to check whether the current user has saved it.
3. Based on the returned `isFavorited`, the heart button shows as "saved" (filled) or "not saved" (outlined).
4. The user clicks the favorite button:
   - If it is "not saved", the frontend calls `POST /api/favorites`. On success the button switches to "saved".
   - If it is "saved", the frontend calls `DELETE /api/favorites/{productId}`. On success the button switches to "not saved".
5. The user opens `/favorites` from `FloatingFavoritesButton.vue` or another entry point.
6. On load, `FavoritesView.vue` calls `GET /api/favorites` to fetch the full list and renders it.
7. On the favorites page the user can browse items or remove them.

## Implementation

### Backend
- **Favorite.java**: the favorite entity.
- **FavoriteMapper.java**: the MyBatis mapper for database access.
- **FavoriteService.java**: the core business logic for adding, removing and listing favorites. Adding and removing both check that the caller is the owner.
- **FavoriteController.java**: the RESTful API exposed to the frontend.

### Frontend
- **State management**: `ProductDetail.vue` usually has a local state variable such as `isFavorited` (ref or data) that controls how the favorite button looks.
- **API calls**: saving and removing favorites are direct API calls and need no complex local state (unlike cart quantities).
- **User experience**: to feel responsive, the frontend should update the UI immediately when the button is clicked ("optimistic update") and then wait for the API. If the call fails, it rolls the UI back and shows an error.
