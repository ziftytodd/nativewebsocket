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
            const ret = { connected: true };
            this.notifyListeners('connected', ret);
        };
        this.webSocket.onmessage = async (msg) => {
            // Got message
            console.log('[NWS] Message received', msg);
            let data = msg.data;
            if (msg.data instanceof ArrayBuffer) {
                data = base64ArrayBuffer(msg.data);
            }
            const ret = { data: data, binary: (msg.data instanceof ArrayBuffer) };
            this.notifyListeners('message', ret);
        };
        this.webSocket.onerror = (err) => {
            // Got error
            console.log('[NWS] ERROR', err);
            this.webSocket = null;
            const ret = { reason: 'PWA Unknown', error: 'PWA Unknown' };
            this.notifyListeners('disconnected', ret);
        };
        this.webSocket.onclose = (closed) => {
            console.log('[NWS] Closed: ' + (closed ? closed.code : 'NoCode') + ' ' + (closed ? closed.reason : 'NoReason'));
            this.webSocket = null;
            const ret = { reason: 'PWA Close', error: 'PWA Close' };
            this.notifyListeners('disconnected', ret);
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
function base64ArrayBuffer(arrayBuffer) {
    let base64 = '';
    const encodings = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    const bytes = new Uint8Array(arrayBuffer);
    const byteLength = bytes.byteLength;
    const byteRemainder = byteLength % 3;
    const mainLength = byteLength - byteRemainder;
    let a;
    let b;
    let c;
    let d;
    let chunk;
    // Main loop deals with bytes in chunks of 3
    for (let i = 0; i < mainLength; i += 3) {
        // Combine the three bytes into a single integer
        chunk = (bytes[i] << 16) | (bytes[i + 1] << 8) | bytes[i + 2];
        // Use bitmasks to extract 6-bit segments from the triplet
        a = (chunk & 16515072) >> 18; // 16515072 = (2^6 - 1) << 18
        b = (chunk & 258048) >> 12; // 258048   = (2^6 - 1) << 12
        c = (chunk & 4032) >> 6; // 4032     = (2^6 - 1) << 6
        d = chunk & 63; // 63       = 2^6 - 1
        // Convert the raw binary segments to the appropriate ASCII encoding
        base64 += encodings[a] + encodings[b] + encodings[c] + encodings[d];
    }
    // Deal with the remaining bytes and padding
    if (byteRemainder === 1) {
        chunk = bytes[mainLength];
        a = (chunk & 252) >> 2; // 252 = (2^6 - 1) << 2
        // Set the 4 least significant bits to zero
        b = (chunk & 3) << 4; // 3   = 2^2 - 1
        base64 += `${encodings[a]}${encodings[b]}==`;
    }
    else if (byteRemainder === 2) {
        chunk = (bytes[mainLength] << 8) | bytes[mainLength + 1];
        a = (chunk & 64512) >> 10; // 64512 = (2^6 - 1) << 10
        b = (chunk & 1008) >> 4; // 1008  = (2^6 - 1) << 4
        // Set the 2 least significant bits to zero
        c = (chunk & 15) << 2; // 15    = 2^4 - 1
        base64 += `${encodings[a]}${encodings[b]}${encodings[c]}=`;
    }
    return base64;
}

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    NativeWebsocketWeb: NativeWebsocketWeb
});

exports.NativeWebsocket = NativeWebsocket;
//# sourceMappingURL=plugin.cjs.js.map
