import { WebPlugin } from '@capacitor/core';
import type { NativeWebsocketPlugin } from './definitions';
export declare class NativeWebsocketWeb extends WebPlugin implements NativeWebsocketPlugin {
    private webSocket;
    connect(options: {
        url: string;
    }): Promise<void>;
    disconnect(): Promise<{
        disconnected: boolean;
    }>;
    send(options: {
        message: string;
    }): Promise<{
        sent: boolean;
    }>;
}
