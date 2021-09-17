import { registerPlugin } from '@capacitor/core';
const NativeWebsocket = registerPlugin('NativeWebsocket', {
//web: () => import('./web').then(m => new m.NativeWebsocketWeb()),
});
export * from './definitions';
export { NativeWebsocket };
//# sourceMappingURL=index.js.map