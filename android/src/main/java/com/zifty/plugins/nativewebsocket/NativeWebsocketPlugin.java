package com.zifty.plugins.nativewebsocket;

// import java.util.Base64;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

@CapacitorPlugin(name = "NativeWebsocket")
public class NativeWebsocketPlugin extends Plugin {

    /** Explicit connection-lost timeout, in seconds, in place of the library default of 60. */
    private static final int CONNECTION_LOST_TIMEOUT_SECONDS = 30;

    /** How long a connect attempt stays in flight before a fresh connect() may take over from it. */
    private static final long CONNECT_TIMEOUT_MILLIS = 30000;

    private final ReentrantLock CONNECT_LOCK = new ReentrantLock();

    /*
     * Everything below is guarded by CONNECT_LOCK. Socket callbacks arrive on the client's own
     * threads, so no field here may be read or written outside it.
     *
     * `ws` doubles as the socket generation: every connect() builds a fresh WebSocketClient, so a
     * callback belongs to the current generation exactly when its client is identical to `ws`. A
     * callback that fails that check is ignored and its client closed - it never touches the state
     * below, which is what holds each generation to at most one `connected` and one `disconnected`.
     */
    private boolean connected = false;
    private boolean connecting = false;
    private long connectTimeoutAt = 0;
    private WebSocketClient ws;

    private static String toBase64String(ByteBuffer buff) {
        ByteBuffer bb = buff.asReadOnlyBuffer();
        bb.position(0);
        byte[] b = new byte[bb.limit()];
        bb.get(b, 0, b.length);
        return Base64.encodeToString(b, Base64.NO_WRAP);
        // return Base64.getEncoder().encodeToString(b);
    }

    @PluginMethod
    public void connect(PluginCall call) {
        String url = call.getString("url");

        // Both are closed after the lock is released: closing a live client takes the client's own
        // monitor, which its callback threads hold while they wait for CONNECT_LOCK.
        WebSocketClient replaced = null;
        WebSocketClient aborted = null;

        CONNECT_LOCK.lock();
        try {
            // Ignore attempt to connect if already connected
            if (connected) {
                call.resolve(new JSObject().put("result", "Already Connected"));
                return;
            }

            // We are already trying to connect and haven't timed out yet
            if (connecting && (connectTimeoutAt > System.currentTimeMillis())) {
                call.resolve(new JSObject().put("result", "Already trying to connect"));
                return;
            }

            // The in-flight guard on the previous attempt has expired. Take that client with us and
            // close it below rather than abandoning it: an abandoned client keeps its connect thread
            // and can still open its socket. It never reached "connected", so it owes JS no
            // disconnected event.
            replaced = takeCurrentClient();

            Map<String, String> headers = new HashMap<>();
            headers.put("Origin", "capacitor://localhost");

            try {
                WebSocketClient client = new WebSocketClient(new URI(url), headers) {
                    @Override
                    public void onMessage(String message) {
                        JSObject ret = new JSObject();
                        ret.put("data", message);
                        ret.put("binary", false);
                        emitFromClient(this, "message", ret);
                    }

                    @Override
                    public void onMessage(ByteBuffer bytes) {
                        JSObject ret = new JSObject();
                        ret.put("data", NativeWebsocketPlugin.toBase64String(bytes));
                        ret.put("binary", true);
                        emitFromClient(this, "message", ret);
                    }

                    @Override
                    public void onOpen(ServerHandshake handshake) {
                        handleOpen(this);
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        handleDisconnect(this, reason, code, null);
                    }

                    @Override
                    public void onError(Exception ex) {
                        handleDisconnect(this, "error", 0, ex.getMessage());
                    }
                };

                // Explicit, rather than inheriting the library default of 60 seconds. Safe to call
                // on a client no other thread can reach yet.
                client.setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT_SECONDS);

                ws = client;
                connecting = true;
                connectTimeoutAt = System.currentTimeMillis() + CONNECT_TIMEOUT_MILLIS;

                client.connect();

                call.resolve(new JSObject().put("result", "Connection Starting"));
            } catch (Exception e) {
                aborted = takeCurrentClient();
                call.reject("Exception occurred: " + e.getMessage());
            }
        } finally {
            CONNECT_LOCK.unlock();
        }

        closeQuietly(replaced);
        closeQuietly(aborted);
    }

    @PluginMethod
    public void send(PluginCall call) {
        WebSocketClient client;
        CONNECT_LOCK.lock();
        try {
            client = currentOpenClient();
        } finally {
            CONNECT_LOCK.unlock();
        }

        if (client == null) {
            forceDisconnect("Websocket not connected");
            call.reject("Websocket not connected");
            return;
        }

        try {
            client.send(call.getString("message"));
            JSObject ret = new JSObject();
            ret.put("sent", true);
            call.resolve(ret);
        } catch (Exception e) {
            forceDisconnect("Exception occurred: " + e.getMessage());
            call.reject("Exception occurred: " + e.getMessage());
        }
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        forceDisconnect("Called disconnect");
        call.resolve(new JSObject());
    }

    /** The current client if it is live and open, otherwise null. Caller holds CONNECT_LOCK. */
    private WebSocketClient currentOpenClient() {
        return (connected && ws != null && ws.isOpen()) ? ws : null;
    }

    /**
     * Emits an event on behalf of a socket callback, but only when that socket is still the current
     * one. Emitting under the lock keeps a late `connected` from overtaking the `disconnected` of a
     * teardown that is already under way.
     */
    private void emitFromClient(WebSocketClient client, String eventName, JSObject data) {
        WebSocketClient stale = null;

        CONNECT_LOCK.lock();
        try {
            if (client != ws) {
                stale = client;
            } else {
                notifyListeners(eventName, data);
            }
        } finally {
            CONNECT_LOCK.unlock();
        }

        closeQuietly(stale);
    }

    private void handleOpen(WebSocketClient client) {
        WebSocketClient stale = null;

        CONNECT_LOCK.lock();
        try {
            if (client != ws) {
                // An abandoned attempt that opened anyway. close() bites now that it is open, which
                // it did not while the client was still connecting.
                stale = client;
            } else {
                connected = true;
                connecting = false;
                connectTimeoutAt = 0;

                JSObject ret = new JSObject();
                ret.put("connected", true);
                notifyListeners("connected", ret);
            }
        } finally {
            CONNECT_LOCK.unlock();
        }

        closeQuietly(stale);
    }

    private void handleDisconnect(WebSocketClient client, String reason, int code, String error) {
        WebSocketClient finished;

        CONNECT_LOCK.lock();
        try {
            if (client != ws) {
                // A stale generation, already accounted for. This is also what stops the onClose
                // that follows an onError from emitting a second disconnected event for the same
                // socket: the onError handler cleared `ws` before the close arrived.
                finished = client;
            } else {
                JSObject ret = new JSObject();
                ret.put("disconnected", true);
                if (reason != null) ret.put("reason", reason);
                ret.put("code", code);
                if (error != null) ret.put("error", error);

                finished = takeCurrentClient();
                notifyListeners("disconnected", ret);
            }
        } finally {
            CONNECT_LOCK.unlock();
        }

        closeQuietly(finished);
    }

    /**
     * Tears down whatever socket exists - connected, still connecting, or already gone - and always
     * tells JS. The client dropped here can never emit again, because its callbacks no longer match
     * the current client, so this stays the only disconnected event for it.
     */
    private void forceDisconnect(String reason) {
        WebSocketClient discarded;

        CONNECT_LOCK.lock();
        try {
            discarded = takeCurrentClient();

            JSObject ret = new JSObject();
            ret.put("disconnected", true);
            ret.put("reason", reason);
            ret.put("code", -1);
            notifyListeners("disconnected", ret);
        } finally {
            CONNECT_LOCK.unlock();
        }

        closeQuietly(discarded);
    }

    /**
     * Clears every trace of the current connection and hands the client back so the caller can close
     * it once the lock is released. Emits nothing. Caller holds CONNECT_LOCK.
     */
    private WebSocketClient takeCurrentClient() {
        WebSocketClient previous = ws;
        ws = null;
        connected = false;
        connecting = false;
        connectTimeoutAt = 0;
        return previous;
    }

    /**
     * Closes a client for good. Must be called with CONNECT_LOCK released: closing takes the
     * client's own monitor, which its callback threads hold while they block on CONNECT_LOCK.
     *
     * <p>{@code WebSocketClient.close()} is a no-op until the handshake has finished - it is gated
     * on a write thread that only exists once the socket is open - so an in-flight client also needs
     * its transport dropped, or its connect thread lives on and may still open the socket. Closing
     * that socket lets the thread fail out on its own, where its callbacks are ignored as stale.
     *
     * <p>{@code closeConnection()} is deliberately not used: it delivers onClose synchronously on
     * the calling thread, which would land straight back here.
     */
    private static void closeQuietly(WebSocketClient client) {
        if (client == null) {
            return;
        }

        boolean wasOpen = client.isOpen();

        try {
            client.close();
        } catch (Exception ignored) {}

        if (wasOpen) {
            return;
        }

        try {
            Socket socket = client.getSocket();
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
    }
}
