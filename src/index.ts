import { registerPlugin } from '@capacitor/core';

import type { NativeWebsocketPlugin } from './definitions';

const NativeWebsocket = registerPlugin<NativeWebsocketPlugin>(
  'NativeWebsocket',
  {
    web: () => import('./web').then(m => new m.NativeWebsocketWeb()),
  },
);

export * from './definitions';
export { NativeWebsocket };
