# Instant Messaging

## Overview

The instant messaging (IM) system lets platform users talk in real time. It is key to helping buyers and sellers communicate, negotiate and resolve problems. Built on WebSocket, it supports one-to-one private chat and lets administrators view and manage every conversation.

### Core features
- **One-to-one chat**: any two users can start a real-time chat. On the product page, buyers can click "Contact Seller" to start a conversation directly.
- **Conversation list**: the chat center shows all of a user's conversations, sorted by the latest message.
- **Real-time messaging**: messages are pushed to the recipient over WebSocket, with no polling.
- **Message history**: opening a chat window loads the recent history automatically, and scrolling up loads more.
- **Unread indicators**: the conversation list and the global floating button show unread counts.
- **Administrator monitoring**: administrators have a dedicated back-office view of every conversation on the platform and can open any of them to read the full history.

## Technology

- **Backend**: Spring Boot WebSocket + STOMP
  - Uses Spring's WebSocket support, with STOMP (Simple Text Oriented Messaging Protocol) as the application protocol to simplify message format and routing.
- **Frontend**: `stompjs/stompjs` + `sockjs-client`
  - `sockjs-client` provides fallbacks (such as long polling) for browsers without WebSocket.
  - `stompjs` implements STOMP so the frontend can easily connect, subscribe and send messages.

## Database schema

### chat_message table
```sql
CREATE TABLE `chat_message` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'Message ID',
  `sender_id` int NOT NULL COMMENT 'Sender ID',
  `recipient_id` int NOT NULL COMMENT 'Recipient ID',
  `content` text NOT NULL COMMENT 'Message content',
  `is_read` tinyint(1) DEFAULT '0' COMMENT 'Whether it has been read',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `sender_id` (`sender_id`),
  KEY `recipient_id` (`recipient_id`),
  CONSTRAINT `chat_message_ibfk_1` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`),
  CONSTRAINT `chat_message_ibfk_2` FOREIGN KEY (`recipient_id`) REFERENCES `user` (`id`)
);
```

## Backend API and WebSocket endpoints

### HTTP API

#### 1. Get message history
`GET /api/chat/history/{userId1}/{userId2}`
- **Authorization**: `Bearer {token}`
- **Response**: the message history between two users (paged).

#### 2. Get the user's conversations
`GET /api/chat/sessions`
- **Authorization**: `Bearer {token}`
- **Response**: the current user's conversations, each with its last message and unread count.

### WebSocket (STOMP) endpoints

- **Connection endpoint**: `/ws`
  - The frontend connects to this HTTP endpoint with SockJS and a STOMP client; Spring upgrades it to a WebSocket connection.

- **Message broker**: `/topic`, `/user`
  - `/topic`: for public broadcasts, rarely used in this system.
  - `/user`: for point-to-point messages. Spring routes messages sent to `/user/{username}/queue/messages` to the user named `{username}`.

- **Application destination**: `/app`
  - The frontend sends messages to destinations prefixed with `/app`, such as `/app/chat`. They are routed to backend methods annotated with `@MessageMapping`.

## Frontend views and components

- **ChatView.vue**: the main chat center page.
  - Route: `/chat`
  - The conversation list is on the left and the selected chat window on the right.

- **ChatWindow.vue**: a standalone chat window component.
  - Used as the right-hand chat panel in `ChatView.vue`.
  - Shows the message stream, handles input and sends messages.

- **AdminChatView.vue**: the administrator's chat monitoring page.
  - Route: `/admin/chat`
  - Lists every user's conversations; the administrator can open any of them.

- **FloatingChatButton.vue**: the global floating chat button.
  - Shows the total unread count and is a shortcut to the chat center.

## Message flow

1.  **Connect**: after the user signs in, the frontend `WebSocketService.js` creates a `StompClient` and opens a WebSocket connection to the backend through `/ws`.
2.  **Subscribe to the private queue**: once connected, the client subscribes to its own private queue, usually `/user/queue/messages`. The backend pushes messages for that user there.
3.  **The user sends a message**:
    -   The user types in the `ChatWindow.vue` input and clicks send.
    -   The frontend calls `stompClient.publish()` to send the message to an application destination such as `/app/chat`.
    -   The payload is a JSON string such as `{ "recipientId": 123, "content": "Hello" }`.
4.  **The backend handles and stores it**:
    -   The `@MessageMapping("/chat")` method in `ChatController.java` receives the message.
    -   It extracts `recipientId` and `content` from the message.
    -   It saves the message (with `senderId`, `recipientId`, `content` and so on) to the `chat_message` table.
5.  **The backend forwards it**:
    -   `ChatController` uses `SimpMessagingTemplate` to send the message to the recipient's private queue.
    -   The destination is `/user/{recipientUsername}/queue/messages`; the Spring Security and WebSocket integration resolves `{recipientUsername}` correctly.
6.  **The recipient receives it**:
    -   The recipient's client has subscribed to the queue, so it receives the message immediately.
    -   The `onMessage` callback fires; the frontend appends the message to the list in `ChatWindow.vue` and scrolls to the bottom.
    -   If the recipient is not in the chat with the sender, the unread count in the conversation list and the badge on `FloatingChatButton.vue` are updated.

## Implementation

### Backend
- **WebSocketConfig.java**: the Spring WebSocket configuration.
  -   Registers the STOMP endpoint (`/ws`).
  -   Configures the message broker with the `/topic` and `/user` prefixes.
  -   Configures the application destination prefix (`/app`).
- **ChatController.java**: contains both `@RestController` methods for HTTP requests and `@MessageMapping` methods for WebSocket messages.
- **ChatMessage.java**: the message entity.
- **ChatService.java**: the chat business logic, such as loading message history and the conversation list.

### Frontend
- **WebSocketService.js**: wraps every `StompClient` operation (connect, disconnect, subscribe, send) and exposes event callbacks (such as `onConnected` and `onMessageReceived`), decoupling it from the Vue components.
- **Vuex store (for example a `chat.js` module)**: manages the global unread count `unreadCount` so components such as `FloatingChatButton` can share it.
- **ChatWindow.vue**: keeps a `messages` array in `ref` or `data`. It updates the array when a message arrives or history loads, and loads more history when the user scrolls up.
