import { Stomp } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class WebSocketService {
    constructor() {
        this.stompClient = null;
        this.connected = false;
        this.reconnecting = false;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
    }

    connect(onMessageReceived) {
        // Do not connect again if already connected or reconnecting
        if (this.connected || this.reconnecting) {
            console.log('[WS] Already connected or connecting...');
            return;
        }

        const token = localStorage.getItem('jwt_token');
        if (!token) {
            console.error("[WS] No token found for WebSocket connection.");
            return;
        }

        const user = JSON.parse(localStorage.getItem('user') || '{}');
        const username = user.username || 'unknown';
        console.log('[WS] Connecting WebSocket... user:', username);

        this.reconnecting = true;

        // Use relative endpoint so it works in dev proxy and Docker Nginx
        const socket = new SockJS('/ws');
        this.stompClient = Stomp.over(socket);

        // Disable verbose debug logs but keep errors
        this.stompClient.debug = (msg) => {
            if (msg.includes('ERROR') || msg.includes('DISCONNECT')) {
                console.error('[WS-DEBUG]', msg);
            }
        };

        const headers = { 'Authorization': `Bearer ${token}` };

        this.stompClient.connect(headers, () => {
            console.log('[WS] ✅ WebSocket connected!');
            console.log('[WS] Authenticated user:', username);
            this.connected = true;
            this.reconnecting = false;
            this.reconnectAttempts = 0;

            // Subscribe to the private message queue
            console.log('[WS] Subscribing to /user/queue/private');
            const subscription = this.stompClient.subscribe('/user/queue/private', (message) => {
                console.log('[WS] 📨 Message received!');
                try {
                    onMessageReceived(message);
                } catch (error) {
                    console.error('[WS] Error while handling message:', error);
                }
            });

            console.log('[WS] ✅ Subscribed, subscription ID:', subscription.id);
            console.log('[WS] Waiting for messages...');
        }, (error) => {
            console.error('[WS] ❌ WebSocket connection failed:', error);
            this.connected = false;
            this.reconnecting = false;

            // Try to reconnect
            if (this.reconnectAttempts < this.maxReconnectAttempts) {
                this.reconnectAttempts++;
                const delay = Math.min(5000 * this.reconnectAttempts, 30000);
                console.log(`[WS] Reconnect attempt ${this.reconnectAttempts} in ${delay/1000} s...`);
                setTimeout(() => {
                    this.connect(onMessageReceived);
                }, delay);
            } else {
                console.error('[WS] Maximum reconnect attempts reached, giving up');
            }
        });
    }

    sendMessage(chatMessage) {
        if (this.stompClient && this.connected) {
            this.stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
        } else {
            console.error('WebSocket is not connected');
        }
    }

    disconnect() {
        if (this.stompClient) {
            this.stompClient.disconnect();
            this.connected = false;
            this.reconnecting = false;
            this.reconnectAttempts = 0;
            console.log('[WS] WebSocket disconnected');
        }
    }

    isConnected() {
        return this.connected;
    }

    reconnect(onMessageReceived) {
        console.log('[WS] Reconnecting manually...');
        this.disconnect();
        this.reconnectAttempts = 0;
        setTimeout(() => {
            this.connect(onMessageReceived);
        }, 1000);
    }
}

export default new WebSocketService();
