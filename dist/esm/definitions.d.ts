import type { PluginListenerHandle } from '@capacitor/core';
export interface ConnectedState {
    connected: boolean;
}
export interface DisconnectedState {
    reason?: string;
    code?: string;
    error?: string;
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
    addListener(eventName: 'connected', listenerFunc: ConnectedChangeListener): Promise<PluginListenerHandle>;
    addListener(eventName: 'disconnected', listenerFunc: DisconnectedChangeListener): Promise<PluginListenerHandle>;
    addListener(eventName: 'message', listenerFunc: MessageListener): Promise<PluginListenerHandle>;
}
