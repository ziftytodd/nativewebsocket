export interface NativeWebsocketPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
