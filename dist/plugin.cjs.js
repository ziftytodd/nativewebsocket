'use strict';

Object.defineProperty(exports, '__esModule', { value: true });

var core = require('@capacitor/core');

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
            console.log('[NWS] Connected');
            this.notifyListeners('connected', { connected: true });
        };
        this.webSocket.onmessage = async (msg) => {
            // Got message
            console.log('[NWS] Message received', msg);
            if (msg.data instanceof ArrayBuffer) {
                this.notifyListeners('message', { data: msg.data, binary: true });
            }
            else {
                this.notifyListeners('message', { data: msg.data, binary: false });
            }
        };
        this.webSocket.onerror = (err) => {
            // Got error
            console.log('[NWS] ERROR', err);
            this.webSocket = null;
            this.notifyListeners('disconnected', { reason: 'unknown', error: 'unknown' });
        };
        this.webSocket.onclose = (closed) => {
            console.log('[NWS] Closed: ' + (closed ? closed.code : 'NoCode') + ' ' + (closed ? closed.reason : 'NoReason'));
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
        console.log('[NWS] Send', options.message);
        if (this.webSocket) {
            this.webSocket.send(options.message);
            return { sent: true };
        }
        return { sent: false };
    }
}

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    NativeWebsocketWeb: NativeWebsocketWeb
});

exports.NativeWebsocket = NativeWebsocket;
//# sourceMappingURL=plugin.cjs.js.map
