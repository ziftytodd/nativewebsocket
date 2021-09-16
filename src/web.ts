import { WebPlugin } from '@capacitor/core';

import type { NativeWebsocketPlugin } from './definitions';

export class NativeWebsocketWeb
  extends WebPlugin
  implements NativeWebsocketPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
