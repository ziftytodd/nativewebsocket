var capacitorNativeWebsocket = (function (exports, core) {
    'use strict';

    const NativeWebsocket = core.registerPlugin('NativeWebsocket', {
        web: () => Promise.resolve().then(function () { return web; }).then(m => new m.NativeWebsocketWeb()),
    });

    class NativeWebsocketWeb extends core.WebPlugin {
        constructor() {
            super(...arguments);
            this.webSocket = null;
        }
        async connect(options) {
            this.webSocket = new WebSocket(options.url);
            this.webSocket.binaryType = 'arraybuffer';
            this.webSocket.onopen = () => {
                // Connected!
                console.log('[WS] Connected');
                this.notifyListeners('connected', { connected: true });
            };
            this.webSocket.onmessage = async (msg) => {
                // Got message
                console.log('[WS] Message received', msg);
                if (msg.data instanceof ArrayBuffer) {
                    this.notifyListeners('message', { data: msg.data, binary: true });
                }
                else {
                    this.notifyListeners('message', { data: msg.data, binary: false });
                }
            };
            this.webSocket.onerror = (err) => {
                // Got error
                console.log('[WS] ERROR', err);
                this.webSocket = null;
                this.notifyListeners('disconnected', { reason: 'unknown', error: 'unknown' });
            };
            this.webSocket.onclose = (closed) => {
                console.log('[WS] Closed: ' + (closed ? closed.code : 'NoCode') + ' ' + (closed ? closed.reason : 'NoReason'));
                this.webSocket = null;
                this.notifyListeners('disconnected', { reason: 'disconnected', error: 'disconnected' });
            };
        }
        async disconnect() {
            if (this.webSocket) {
                this.webSocket.close();
                this.webSocket = null;
            }
            return { disconnected: true };
        }
        async send(options) {
            console.log('[WS] Send', options.message);
            if (this.webSocket) {
                this.webSocket.send(options.message);
                return { sent: true };
            }
            return { sent: false };
        }
    }
    //
    //
    // export interface NativeWebsocketPlugin {
    //     connect(options: { url: string }): Promise<void>;
    //     send(options: { message: string }): Promise<{ sent: boolean }>;
    //     disconnect(): Promise<{ disconnected: boolean }>;
    //
    //     addListener(
    //         eventName: 'connected',
    //         listenerFunc: ConnectedChangeListener,
    //     ): Promise<PluginListenerHandle> & PluginListenerHandle;
    //
    //     addListener(
    //         eventName: 'disconnected',
    //         listenerFunc: DisconnectedChangeListener,
    //     ): Promise<PluginListenerHandle> & PluginListenerHandle;
    //
    //     addListener(
    //         eventName: 'message',
    //         listenerFunc: MessageListener,
    //     ): Promise<PluginListenerHandle> & PluginListenerHandle;
    //
    // }

    var web = /*#__PURE__*/Object.freeze({
        __proto__: null,
        NativeWebsocketWeb: NativeWebsocketWeb
    });

    exports.NativeWebsocket = NativeWebsocket;

    Object.defineProperty(exports, '__esModule', { value: true });

    return exports;

}({}, capacitorExports));
//# sourceMappingURL=plugin.js.map
