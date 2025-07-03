# native-websocket

Provides native websocket client functionality for iOS and Android

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

| Prop         | Type                |
| ------------ | ------------------- |
| **`reason`** | <code>string</code> |
| **`code`**   | <code>string</code> |
| **`error`**  | <code>string</code> |


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
