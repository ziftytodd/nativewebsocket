import type { PluginListenerHandle } from '@capacitor/core';
export interface ConnectedState {
    connected: boolean;
}
export interface DisconnectedState {
    reason?: string;
    /**
     * WebSocket close code, or -1 when the plugin itself tore the socket down.
     */
    code?: number;
    error?: string;
    /**
     * HTTP status of a failed upgrade handshake, when the platform exposes it.
     * Omitted when the status is not known.
     */
    httpStatus?: number;
}
export interface MessageEvent {
    data: string;
    binary: boolean;
}
export declare type ConnectedChangeListener = (state: ConnectedState) => void;
export declare type DisconnectedChangeListener = (state: DisconnectedState) => void;
export declare type MessageListener = (event: MessageEvent) => void;
export interface NativeWebsocketPlugin {
    connect(options: {
        url: string;
    }): Promise<{
        result: string;
    }>;
    send(options: {
        message: string;
    }): Promise<{
        sent: boolean;
    }>;
    disconnect(): Promise<{
        disconnected: boolean;
    }>;
    /**
     * Resolves true only while the plugin holds a live, open socket.
     * Use it to resync after missing an event; it never rejects.
     */
    isConnected(): Promise<{
        connected: boolean;
    }>;
    addListener(eventName: 'connected', listenerFunc: ConnectedChangeListener): Promise<PluginListenerHandle>;
    addListener(eventName: 'disconnected', listenerFunc: DisconnectedChangeListener): Promise<PluginListenerHandle>;
    addListener(eventName: 'message', listenerFunc: MessageListener): Promise<PluginListenerHandle>;
}
