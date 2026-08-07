# native-websocket

Provides native websocket client functionality for iOS and Android

## Compatibility

Version 1.0.0 and later require Capacitor 8.

The iOS side is Swift Package Manager only.
CocoaPods is not supported: as of 1.1.0 the package ships no podspec, so an app whose iOS project integrates through CocoaPods cannot consume this plugin.
Consuming apps must use Capacitor's Swift Package Manager support, which picks the plugin up through its `Package.swift`.

## Install

```bash
npm install native-websocket
npx cap sync
```

## API

<docgen-index>

* [`connect(...)`](#connect)
* [`send(...)`](#send)
* [`disconnect()`](#disconnect)
* [`isConnected()`](#isconnected)
* [`addListener('connected', ...)`](#addlistenerconnected-)
* [`addListener('disconnected', ...)`](#addlistenerdisconnected-)
* [`addListener('message', ...)`](#addlistenermessage-)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### connect(...)

```typescript
connect(options: { url: string; }) => Promise<{ result: string; }>
```

| Param         | Type                          |
| ------------- | ----------------------------- |
| **`options`** | <code>{ url: string; }</code> |

**Returns:** <code>Promise&lt;{ result: string; }&gt;</code>

--------------------


### send(...)

```typescript
send(options: { message: string; }) => Promise<{ sent: boolean; }>
```

| Param         | Type                              |
| ------------- | --------------------------------- |
| **`options`** | <code>{ message: string; }</code> |

**Returns:** <code>Promise&lt;{ sent: boolean; }&gt;</code>

--------------------


### disconnect()

```typescript
disconnect() => Promise<{ disconnected: boolean; }>
```

**Returns:** <code>Promise&lt;{ disconnected: boolean; }&gt;</code>

--------------------


### isConnected()

```typescript
isConnected() => Promise<{ connected: boolean; }>
```

Resolves true only while the plugin holds a live, open socket.
Use it to resync after missing an event; it never rejects.

**Returns:** <code>Promise&lt;{ connected: boolean; }&gt;</code>

--------------------


### addListener('connected', ...)

```typescript
addListener(eventName: 'connected', listenerFunc: ConnectedChangeListener) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                        |
| ------------------ | --------------------------------------------------------------------------- |
| **`eventName`**    | <code>'connected'</code>                                                    |
| **`listenerFunc`** | <code><a href="#connectedchangelistener">ConnectedChangeListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('disconnected', ...)

```typescript
addListener(eventName: 'disconnected', listenerFunc: DisconnectedChangeListener) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                              |
| ------------------ | --------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'disconnected'</code>                                                       |
| **`listenerFunc`** | <code><a href="#disconnectedchangelistener">DisconnectedChangeListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('message', ...)

```typescript
addListener(eventName: 'message', listenerFunc: MessageListener) => Promise<PluginListenerHandle>
```

| Param              | Type                                                        |
| ------------------ | ----------------------------------------------------------- |
| **`eventName`**    | <code>'message'</code>                                      |
| **`listenerFunc`** | <code><a href="#messagelistener">MessageListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### Interfaces


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### ConnectedState

| Prop            | Type                 |
| --------------- | -------------------- |
| **`connected`** | <code>boolean</code> |


#### DisconnectedState

| Prop             | Type                | Description                                                                                                                                                                            |
| ---------------- | ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`reason`**     | <code>string</code> |                                                                                                                                                                                        |
| **`code`**       | <code>number</code> | WebSocket close code. -1 when the plugin itself tore the socket down, and 0 for a terminal event that carries no close code at all - on iOS the cancelled, error and peerClosed cases. |
| **`error`**      | <code>string</code> |                                                                                                                                                                                        |
| **`httpStatus`** | <code>number</code> | HTTP status of a failed upgrade handshake, when the platform exposes it. Omitted when the status is not known.                                                                         |


#### MessageEvent

| Prop         | Type                 |
| ------------ | -------------------- |
| **`data`**   | <code>string</code>  |
| **`binary`** | <code>boolean</code> |


### Type Aliases


#### ConnectedChangeListener

<code>(state: <a href="#connectedstate">ConnectedState</a>): void</code>


#### DisconnectedChangeListener

<code>(state: <a href="#disconnectedstate">DisconnectedState</a>): void</code>


#### MessageListener

<code>(event: <a href="#messageevent">MessageEvent</a>): void</code>

</docgen-api>
