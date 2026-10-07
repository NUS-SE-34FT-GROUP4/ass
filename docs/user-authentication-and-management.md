# User Authentication and Management

## Overview

The system manages the whole user lifecycle in two parts: user authentication and back-office administration.

### 1. User authentication
- **Registration**: new users register with an email and password.
- **Login**: registered users sign in with their email and password; the system returns a JWT for subsequent authentication.
- **Forgot password**: users can recover their password through their registered email.
- **Sign out**: users clear the local authentication data and sign out safely.
- **JWT authentication**: the system uses JWT (JSON Web Token) for stateless authentication to protect the API.

### 2. Back-office administration
- **User list**: administrators see every user with their basic details and roles.
- **Ban/unban**: administrators can disable or enable a user account.
- **Role management**: administrators assign roles (such as `admin` and `user`) to users.

## Database schema

### user table
```sql
CREATE TABLE `user` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'User ID',
  `username` varchar(50) NOT NULL COMMENT 'Username',
  `password` varchar(255) NOT NULL COMMENT 'Password',
  `email` varchar(100) NOT NULL COMMENT 'Email',
  `avatar` varchar(255) DEFAULT NULL COMMENT 'Avatar URL',
  `balance` decimal(10,2) DEFAULT '0.00' COMMENT 'Balance',
  `payment_password` varchar(255) DEFAULT NULL COMMENT 'Payment password',
  `is_enabled` tinyint(1) DEFAULT '1' COMMENT 'Whether the account is enabled',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`)
);
```

### role table
```sql
CREATE TABLE `role` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
);
```

### user_role table (user-role link)
```sql
CREATE TABLE `user_role` (
  `user_id` int NOT NULL,
  `role_id` int NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `role_id` (`role_id`),
  CONSTRAINT `user_role_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `user_role_ibfk_2` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
);
```

## Backend API

### Authentication endpoints

#### 1. Register
`POST /api/auth/register`
- **Request Body**: `{ "username": "testuser", "password": "password123", "email": "test@example.com" }`
- **Response**: a success or failure message.

#### 2. Log in
`POST /api/auth/login`
- **Request Body**: `{ "username": "testuser", "password": "password123" }`
- **Response**: `{ "success": true, "token": "jwt-token-string", "user": { ... } }`

#### 3. Forgot password
`POST /api/auth/forgot-password`
- **Request Body**: `{ "email": "test@example.com" }`
- **Response**: confirmation that the password reset email was sent.

### Administration endpoints

#### 1. List all users
`GET /api/admin/users`
- **Authorization**: `Bearer {admin-token}`
- **Response**: a list with every user's details.

#### 2. Toggle a user's status (ban/unban)
`PUT /api/admin/users/{userId}/toggle-status`
- **Authorization**: `Bearer {admin-token}`
- **Response**: a success or failure message.

## Frontend views and components

### Authentication views
- **RegisterView.vue**: the registration page, with a form for username, email and password.
  - Route: `/register`
- **LoginView.vue**: the login page, with a form for username/email and password.
  - Route: `/login`
- **ForgotPasswordView.vue**: the forgot password page, where users enter their registered email to receive a reset link.
  - Route: `/forgot-password`

### Administration views
- **AdminUsers.vue**: the administrator's user management page.
  - Route: `/admin/users`
  - Features: lists users with search and ban/unban buttons.

### Core logic files
- **frontend/src/store/auth.js**: the store module managing login state, the token and user details.
- **frontend/src/router/index.js**: the router configuration, with route guards that check whether the user is signed in and has administrator access.

## Flow

### User authentication flow
1. A new user opens `/register` and fills in the form to register.
2. A registered user opens `/login` and signs in with their credentials.
3. After a successful login, `auth.js` stores the token and user details and saves them to `localStorage`.
4. The HTTP client (such as axios) adds `Authorization: Bearer {token}` to the headers of every request.
5. When the user opens a page that requires sign-in, the route guard checks the login state in `auth.js` and redirects to the login page if needed.
6. When the user clicks "Sign Out", `auth.js` clears the authentication data from `state` and `localStorage`.

### Administration flow
1. An administrator signs in.
2. Opens `/admin/users`.
3. The route guard checks the user has the `admin` role and refuses access otherwise.
4. On load the page calls `GET /api/admin/users` and shows every user.
5. The administrator can click a button to ban or unban a particular user.

## Implementation

### Backend
- **SecurityConfig.java**: the core Spring Security configuration, defining the authentication logic, password encoder (BCrypt), JWT filter and which URLs are protected.
- **JwtTokenProvider.java**: a utility that generates and validates JWTs.
- **UserDetailsServiceImpl.java**: implements Spring Security's `UserDetailsService` to load a user from the database by username.
- **AuthController.java**: the controller for registration, login and other authentication requests.
- **AdminController.java**: the controller for administrator operations.

### Frontend
- **Vuex (auth.js)**:
  - `state`: holds `token`, `user` and `isAuthenticated`.
  - `mutations`: synchronous methods that change `state`, such as `SET_USER` and `LOGOUT`.
  - `actions`: asynchronous login, registration and sign-out operations that call the API and commit `mutations`.
- **Router Guards (router/index.js)**:
  - `beforeEach`: a global guard that runs before every navigation.
  - Checks `meta.requiresAuth` to see whether the page requires sign-in.
  - Checks `meta.requiresAdmin` to see whether the page requires administrator access.
