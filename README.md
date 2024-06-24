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
* [`addListener(...)`](#addlistener)
* [`addListener(...)`](#addlistener)
* [`addListener(...)`](#addlistener)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### connect(...)

```typescript
connect(options: { url: string; }) => any
```

| Param         | Type                          |
| ------------- | ----------------------------- |
| **`options`** | <code>{ url: string; }</code> |

**Returns:** <code>any</code>

--------------------


### send(...)

```typescript
send(options: { message: string; }) => any
```

| Param         | Type                              |
| ------------- | --------------------------------- |
| **`options`** | <code>{ message: string; }</code> |

**Returns:** <code>any</code>

--------------------


### disconnect()

```typescript
disconnect() => any
```

**Returns:** <code>any</code>

--------------------


### addListener(...)

```typescript
addListener(eventName: 'connected', listenerFunc: ConnectedChangeListener) => any
```

| Param              | Type                                            |
| ------------------ | ----------------------------------------------- |
| **`eventName`**    | <code>"connected"</code>                        |
| **`listenerFunc`** | <code>(state: ConnectedState) =&gt; void</code> |

**Returns:** <code>any</code>

--------------------


### addListener(...)

```typescript
addListener(eventName: 'disconnected', listenerFunc: DisconnectedChangeListener) => any
```

| Param              | Type                                               |
| ------------------ | -------------------------------------------------- |
| **`eventName`**    | <code>"disconnected"</code>                        |
| **`listenerFunc`** | <code>(state: DisconnectedState) =&gt; void</code> |

**Returns:** <code>any</code>

--------------------


### addListener(...)

```typescript
addListener(eventName: 'message', listenerFunc: MessageListener) => any
```

| Param              | Type                                          |
| ------------------ | --------------------------------------------- |
| **`eventName`**    | <code>"message"</code>                        |
| **`listenerFunc`** | <code>(event: MessageEvent) =&gt; void</code> |

**Returns:** <code>any</code>

--------------------


### Interfaces


#### PluginListenerHandle

| Prop         | Type                      |
| ------------ | ------------------------- |
| **`remove`** | <code>() =&gt; any</code> |

</docgen-api>
