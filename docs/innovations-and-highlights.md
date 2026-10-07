# Innovations and Highlights

## Introduction

This project is a modern C2C (consumer-to-consumer) second-hand trading platform. Beyond the core features of a standard e-commerce site, it adds several innovative business features and technical highlights, aiming to build a complete, smooth, safe and reliable marketplace for second-hand items on campus.

## Core innovations

### 1. A complete social commerce loop
The platform is more than a product listing site. It covers the full trading loop from browsing to buying to feedback, with a stronger social side built on top.
- **Real-time communication**: a built-in WebSocket instant messaging system lets buyers and sellers talk before and after a sale without delay.
- **Playful bargaining**: the "bargain" feature lets users get a discount by inviting friends to help, making shopping more fun and easier to share.
- **Two-way reviews and credit**: modelled on mainstream e-commerce platforms, a full review and credit score system lets both sides rate each other. The system calculates credit scores and levels automatically, giving users data to decide with and building trust on the platform.

### 2. A realistic simulated payment experience
To bring the system closer to real-world trading, we designed a complete simulated payment system.
- **Account balance**: every user has their own virtual account balance.
- **Payment password**: before sensitive actions such as paying or withdrawing, users enter a separate payment password, which makes accounts more secure.
- **Combined payment for several orders**: the cart can combine items from different sellers; at payment time they are split into several sub-orders automatically, while the user sees a single payment step.

### 3. Full back-office management and monitoring
Besides rich user features, the system gives administrators a powerful back office to keep the platform under control and safe.
- **User management**: administrators can see every user and ban or unban them.
- **Content moderation**: administrators review reports submitted by users and act on items or users that break the rules.
- **Chat monitoring**: for platform safety, administrators can view every chat on the platform, so they can investigate trading disputes or rule violations.

## Technical highlights

### 1. Separated frontend and backend
- **Backend (Spring Boot)**: a classic Java enterprise framework providing a stable, high-performance RESTful API. Spring Security handles access control, Spring WebSocket provides real-time communication, and MyBatis is the persistence layer.
- **Frontend (Vue.js)**: the popular progressive JavaScript framework Vue.js builds a responsive single-page application (SPA). Vue Router handles routing and Vuex global state, enabling efficient development of complex scenarios and a smooth user experience.

### 2. Real-time communication over WebSocket
- The instant messaging (IM) system is built on **Spring WebSocket + the STOMP protocol**. Compared with traditional HTTP polling, WebSocket provides a persistent full-duplex connection that greatly reduces latency and server load, giving users a desktop-app-like real-time chat.

### 3. MinIO object storage
- All media uploaded by users (such as product images and avatars) is stored in a **MinIO** object storage server rather than on the application server's disk or in the database. This decouples static assets from the application, is highly available and easy to scale horizontally, and is a best practice for modern cloud-native applications.

### 4. Stateless JWT authentication
- User authentication is based on **JSON Web Tokens (JWT)**. After login the backend issues a time-limited token, which the frontend sends with every subsequent API request. This stateless scheme means the server keeps no session state, which suits distributed systems and separated frontend/backend architectures.

### 5. DevOps and containerised deployment
- The project ships complete **Docker** and **Docker Compose** configuration. A single command (`docker-compose up`) starts every service, including the frontend (Nginx), backend, database (MySQL) and object storage (MinIO), greatly simplifying environment setup and deployment.
