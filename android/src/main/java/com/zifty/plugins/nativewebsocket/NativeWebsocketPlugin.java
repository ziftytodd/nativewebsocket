package com.zifty.plugins.nativewebsocket;

// import java.util.Base64;
import android.os.SystemClock;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLParameters;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;

@CapacitorPlugin(name = "NativeWebsocket")
public class NativeWebsocketPlugin extends Plugin {

    /** Explicit connection-lost timeout, in seconds, in place of the library default of 60. */
    private static final int CONNECTION_LOST_TIMEOUT_SECONDS = 30;

    /**
     * Bounds each blocking phase of a connect attempt: first the TCP connect, then the wait for the
     * upgrade response. The library leaves both unbounded on its own. It is also how long an
     * attempt stays in flight before a fresh connect() may take over from it.
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 30000;

    /**
     * How long a graceful close may run before its transport is dropped anyway. The library has no
     * close-handshake timeout, and the connection-lost timer only catches a peer that has gone
     * silent, so without this a peer that keeps answering pings could hold a closed socket open.
     */
    private static final long CLOSE_HANDSHAKE_TIMEOUT_MILLIS = 10000;

    /**
     * Runs the bounded follow-up to a graceful close, and nothing else. One daemon thread shared by
     * every client, allowed to die whenever no close is outstanding, so an app that never closes a
     * socket carries no thread at all.
     */
    private static final ScheduledThreadPoolExecutor CLOSE_WATCHDOG = newCloseWatchdog();

    private static ScheduledThreadPoolExecutor newCloseWatchdog() {
        ScheduledThreadPoolExecutor watchdog = new ScheduledThreadPoolExecutor(1, (runnable) -> {
            Thread thread = new Thread(runnable, "NativeWebsocketCloseWatchdog");
            thread.setDaemon(true);
            return thread;
        });
        // Without this a cancelled task sits in the queue until its delay elapses, keeping the
        // thread alive for a close that has already finished.
        watchdog.setRemoveOnCancelPolicy(true);
        watchdog.setKeepAliveTime(CLOSE_HANDSHAKE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        watchdog.allowCoreThreadTimeOut(true);
        return watchdog;
    }

    private final ReentrantLock CONNECT_LOCK = new ReentrantLock();

    /*
     * Everything below is guarded by CONNECT_LOCK. Socket callbacks arrive on the client's own
     * threads, so no field here may be read or written outside it.
     *
     * `ws` doubles as the socket generation: every connect() builds a fresh client, so a callback
     * belongs to the current generation exactly when its client is identical to `ws`. A callback
     * that fails that check is ignored and its client killed - it never touches the state below,
     * which is what holds each generation to at most one `connected` and one `disconnected`.
     */
    private boolean connected = false;
    private boolean connecting = false;
    private long connectTimeoutAt = 0;
    private TrackedClient ws;
    private Integer handshakeHttpStatus;

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
        if (url == null || url.trim().isEmpty()) {
            call.reject("Must provide a url");
            return;
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            call.reject("Exception occurred: " + e.getMessage());
            return;
        }

        // Contract item 3 wants the expired attempt torn down before its replacement exists, so this
        // runs as two locked sections with the teardown between them. Overlapping them would let
        // both clients reach the server at once, and a backend that allows one session per driver
        // would let the dying attempt displace the fresh one.
        TrackedClient expired;
        CONNECT_LOCK.lock();
        try {
            JSObject busy = busyResult();
            if (busy != null) {
                call.resolve(busy);
                return;
            }
            expired = takeCurrentClient();
        } finally {
            CONNECT_LOCK.unlock();
        }

        // Superseding an expired, never-opened attempt is silent per contract item 3: JS initiated
        // the replacement and was never told the old attempt opened.
        killQuietly(expired);

        TrackedClient aborted = null;
        CONNECT_LOCK.lock();
        try {
            // Re-checked, because the lock was released for the teardown above.
            JSObject busy = busyResult();
            if (busy != null) {
                call.resolve(busy);
                return;
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("Origin", "capacitor://localhost");

            try {
                TrackedClient client = new TrackedClient(uri, headers);

                // Explicit, rather than inheriting the library default of 60 seconds. Safe to call
                // on a client no other thread can reach yet.
                client.setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT_SECONDS);

                ws = client;
                connecting = true;
                connectTimeoutAt = SystemClock.elapsedRealtime() + CONNECT_TIMEOUT_MILLIS;

                client.connect();

                call.resolve(new JSObject().put("result", "Connection Starting"));
            } catch (Exception e) {
                aborted = takeCurrentClient();
                call.reject("Exception occurred: " + e.getMessage());
            }
        } finally {
            CONNECT_LOCK.unlock();
        }

        killQuietly(aborted);
    }

    /**
     * The connect() result for a call that must not start a new attempt, or null to go ahead.
     * Shared by both locked sections of connect() so the two guards cannot drift apart. Caller
     * holds CONNECT_LOCK.
     */
    private JSObject busyResult() {
        if (connected) {
            return new JSObject().put("result", "Already Connected");
        }

        // elapsedRealtime, not the wall clock: an NTP correction or a manual date change must not
        // hold a dead attempt open past 30s, nor supersede a healthy one early. Mirrors the iOS
        // monotonic guard, with the platform's usual difference that this one counts sleep too.
        if (connecting && (connectTimeoutAt > SystemClock.elapsedRealtime())) {
            return new JSObject().put("result", "Already trying to connect");
        }

        return null;
    }

    @PluginMethod
    public void send(PluginCall call) {
        String message = call.getString("message");
        if (message == null) {
            call.reject("Must provide a message");
            return;
        }

        TrackedClient client;
        CONNECT_LOCK.lock();
        try {
            client = currentOpenClient();
        } finally {
            CONNECT_LOCK.unlock();
        }

        if (client == null) {
            // Deliberate: this tears down an in-flight attempt as well, and tells JS it did.
            //
            // A caller only reaches here by sending while the plugin is not connected, which means
            // its own view has diverged from the plugin's - the stale-connected case the audit
            // opened with. The teardown plus the retained `disconnected` is the resync that corrects
            // that, and contract item 2 requires internal force-disconnect paths to tear down
            // regardless of connecting state. Callers that want to ask without consequences have
            // isConnected().
            forceDisconnect("Websocket not connected");
            call.reject("Websocket not connected");
            return;
        }

        try {
            client.send(message);
            JSObject ret = new JSObject();
            ret.put("sent", true);
            call.resolve(ret);
        } catch (Exception e) {
            String failure = "Exception occurred: " + e.getMessage();
            // The socket can go down between the check above and this failure, in which case its own
            // terminal callback has already told JS; a second event here would be two disconnects
            // for one drop.
            disconnectIfCurrent(client, failure);
            call.reject(failure);
        }
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        forceDisconnect("Called disconnect");
        call.resolve(new JSObject().put("disconnected", true));
    }

    @PluginMethod
    public void isConnected(PluginCall call) {
        CONNECT_LOCK.lock();
        try {
            call.resolve(new JSObject().put("connected", currentOpenClient() != null));
        } finally {
            CONNECT_LOCK.unlock();
        }
    }

    /** The current client if it is live and open, otherwise null. Caller holds CONNECT_LOCK. */
    private TrackedClient currentOpenClient() {
        return (connected && ws != null && ws.isOpen()) ? ws : null;
    }

    /**
     * Emits an event on behalf of a socket callback, but only when that socket is still the current
     * one. Emitting under the lock keeps a late `connected` from overtaking the `disconnected` of a
     * teardown that is already under way.
     */
    private void emitFromClient(TrackedClient client, String eventName, JSObject data) {
        TrackedClient stale = null;

        CONNECT_LOCK.lock();
        try {
            if (client != ws) {
                stale = client;
            } else {
                notifyListeners(eventName, data, true);
            }
        } finally {
            CONNECT_LOCK.unlock();
        }

        killQuietly(stale);
    }

    private void handleOpen(TrackedClient client) {
        TrackedClient stale = null;

        CONNECT_LOCK.lock();
        try {
            if (client != ws) {
                // An abandoned attempt that opened anyway. close() bites now that it is open.
                stale = client;
            } else {
                connected = true;
                connecting = false;
                connectTimeoutAt = 0;
                handshakeHttpStatus = null; // the handshake succeeded, so there is nothing to report

                JSObject ret = new JSObject();
                ret.put("connected", true);
                notifyListeners("connected", ret, true);
            }
        } finally {
            CONNECT_LOCK.unlock();
        }

        killQuietly(stale);
    }

    private void handleDisconnect(TrackedClient client, String reason, int code, String error) {
        TrackedClient finished;

        CONNECT_LOCK.lock();
        try {
            if (client != ws) {
                // A stale generation, already accounted for. This is also what stops the onClose
                // that follows an onError from emitting a second disconnected event for the same
                // socket: the onError handler cleared `ws` before the close arrived.
                finished = client;
            } else {
                JSObject ret = disconnectPayload(reason, code);
                if (error != null) ret.put("error", error);

                Integer httpStatus = resolveHandshakeHttpStatus(reason, error);
                if (httpStatus != null) ret.put("httpStatus", httpStatus.intValue());

                finished = takeCurrentClient();
                notifyListeners("disconnected", ret, true);
            }
        } finally {
            CONNECT_LOCK.unlock();
        }

        killQuietly(finished);
    }

    /**
     * Tears down whatever socket exists - connected, still connecting, or already gone - and always
     * tells JS. The client dropped here can never emit again, because its callbacks no longer match
     * the current client, so this stays the only disconnected event for it.
     */
    private void forceDisconnect(String reason) {
        TrackedClient discarded;

        CONNECT_LOCK.lock();
        try {
            discarded = takeCurrentClient();
            notifyListeners("disconnected", disconnectPayload(reason, -1), true);
        } finally {
            CONNECT_LOCK.unlock();
        }

        killQuietly(discarded);
    }

    /**
     * Tears down and reports a client only if it is still the current one. Used where the caller
     * has been holding a client reference across an unlocked stretch and the socket may have gone
     * down under it in the meantime, having already reported itself.
     */
    private void disconnectIfCurrent(TrackedClient client, String reason) {
        TrackedClient discarded = null;

        CONNECT_LOCK.lock();
        try {
            if (client == ws) {
                discarded = takeCurrentClient();
                notifyListeners("disconnected", disconnectPayload(reason, -1), true);
            }
        } finally {
            CONNECT_LOCK.unlock();
        }

        killQuietly(discarded);
    }

    private static JSObject disconnectPayload(String reason, int code) {
        JSObject ret = new JSObject();
        ret.put("disconnected", true);
        if (reason != null) ret.put("reason", reason);
        ret.put("code", code);
        return ret;
    }

    private void recordHandshakeStatus(TrackedClient client, short httpStatus) {
        CONNECT_LOCK.lock();
        try {
            if (client == ws) {
                handshakeHttpStatus = (int) httpStatus;
            }
        } finally {
            CONNECT_LOCK.unlock();
        }
    }

    /**
     * The HTTP status of the upgrade this generation failed on, or null when it is unknown. Caller
     * holds CONNECT_LOCK.
     *
     * <p>The recorded slot is generation-scoped and cleared on open, so it stands on its own. The
     * message parse does not: an established server may close with any reason text it likes,
     * including one that quotes the library's wording. It is therefore only consulted for a
     * generation that never opened, where the sole thing that can produce that text is the library
     * refusing the upgrade. See {@link HandshakeStatus} for why the callback alone is not enough.
     */
    private Integer resolveHandshakeHttpStatus(String reason, String error) {
        if (handshakeHttpStatus != null) {
            return handshakeHttpStatus;
        }

        if (connected) {
            return null;
        }

        Integer fromReason = HandshakeStatus.parse(reason);
        return (fromReason != null) ? fromReason : HandshakeStatus.parse(error);
    }

    /**
     * Clears every trace of the current connection and hands the client back so the caller can kill
     * it once the lock is released. Emits nothing. Caller holds CONNECT_LOCK.
     */
    private TrackedClient takeCurrentClient() {
        TrackedClient previous = ws;
        ws = null;
        connected = false;
        connecting = false;
        connectTimeoutAt = 0;
        handshakeHttpStatus = null;
        return previous;
    }

    /** Ends a client, if there is one. Must be called with CONNECT_LOCK released. */
    private static void killQuietly(TrackedClient client) {
        if (client != null) {
            client.kill();
        }
    }

    /**
     * The plugin's websocket client, extended with the identity and shutdown machinery the library
     * does not offer.
     *
     * <p>Java-WebSocket 1.5.2 gives an owner no way to cancel a connect attempt from outside.
     * {@code close()} reaches the engine only once {@code run()} has sent the upgrade request and
     * started the write thread, and until then the raw socket lives in a non-volatile field written
     * by the connect thread, so another thread may not even see it.
     *
     * <p>So the client polices itself. {@link #kill()} raises a flag, and the connect thread checks
     * it at every point the library hands control back: before it resolves the host, after the TLS
     * wrap, and last of all just before the upgrade request is handed over for writing. Each check
     * runs on the thread that assigned the socket, so it can close its own transport with no
     * publication question, and each republishes the socket so a killer on another thread can close
     * it from that point on.
     *
     * <p>That narrows the window; it does not close it. The final check still returns to the
     * library, which then queues the request for the write thread. A kill landing in that gap races
     * the writer and can lose, in which case one upgrade request reaches the server and the
     * transport close that follows immediately ends the session it opened. The gap is microseconds
     * wide and there is no later interception point on this version - {@code onWriteDemand} is
     * final and the write thread is not reachable - so the residual race is accepted, not solved.
     */
    private final class TrackedClient extends WebSocketClient {

        private final AtomicBoolean killed = new AtomicBoolean(false);

        /**
         * Decides, once, whether the close handshake or its watchdog gets to bound this client's
         * transport. Whoever wins the compare-and-set acts; the loser does nothing.
         */
        private final AtomicBoolean closeBounded = new AtomicBoolean(false);

        /**
         * The raw socket, republished from the library's non-volatile field by the very thread that
         * assigned it, so other threads can reliably force the transport down from here on.
         */
        private volatile Socket transport;

        /** Pending force-drop for a graceful close that has not completed yet. */
        private volatile ScheduledFuture<?> closeWatchdog;

        TrackedClient(URI uri, Map<String, String> headers) {
            // The four-argument constructor is the only one that bounds the TCP connect; the shorter
            // ones pass 0, which waits forever.
            super(uri, new Draft_6455(), headers, CONNECT_TIMEOUT_MILLIS);

            // The same lookup the library installs by default, wrapped so that the connect thread
            // publishes its socket and honours a kill before it opens a TCP connection at all. This
            // is the earliest point the library offers, and it is what makes closeTransport() useful
            // for the whole of the connect phase rather than only from the handshake onwards.
            setDnsResolver((target) -> {
                if (supersededAfterPublishingTransport()) {
                    throw new UnknownHostException("Connection superseded");
                }
                return InetAddress.getByName(target.getHost());
            });
        }

        @Override
        protected void onSetSSLParameters(SSLParameters sslParameters) {
            // The library's own implementation turns on hostname validation, and its javadoc
            // requires the super call to keep it.
            super.onSetSSLParameters(sslParameters);

            // Runs on the connect thread once the socket has been wrapped for TLS but before the
            // streams are taken. upgradeSocketToSSL() only layers the socket - the TLS handshake
            // itself happens on the first read or write, which is from here on - so this is where
            // the read timeout has to be set for the handshake to be bounded at all.
            if (supersededAfterPublishingTransport()) {
                throw new IllegalStateException("Connection superseded");
            }

            setReadTimeout(CONNECT_TIMEOUT_MILLIS);
        }

        @Override
        public void onWebsocketHandshakeSentAsClient(WebSocket conn, ClientHandshake request) throws InvalidDataException {
            // Runs on the connect thread inside startHandshake(), after TCP and any TLS setup but
            // before the library queues the upgrade request. This is the last point at which the
            // client gets control, so it is where the kill flag is enforced; a kill landing after
            // this returns races the write thread and can lose, which is the residual gap documented
            // on the class.
            if (supersededAfterPublishingTransport()) {
                throw new InvalidDataException(CloseFrame.NEVER_CONNECTED, "Connection superseded");
            }

            // The library then waits indefinitely for the upgrade response - the connection-lost
            // timer only starts once the socket is open. Bound that wait here; onOpen clears it.
            // Repeated from onSetSSLParameters, which plain ws:// never reaches.
            setReadTimeout(CONNECT_TIMEOUT_MILLIS);

            super.onWebsocketHandshakeSentAsClient(conn, request);
        }

        @Override
        public void onWebsocketHandshakeReceivedAsClient(WebSocket conn, ClientHandshake request, ServerHandshake response)
            throws InvalidDataException {
            super.onWebsocketHandshakeReceivedAsClient(conn, request, response);
            recordHandshakeStatus(this, response.getHttpStatus());
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            // The handshake is in; liveness is the connection-lost timer's job from here, and a read
            // timeout would now fire on a healthy idle socket.
            setReadTimeout(0);
            handleOpen(this);
        }

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
        public void onClose(int code, String reason, boolean remote) {
            cancelCloseWatchdog();
            handleDisconnect(this, reason, code, null);
        }

        @Override
        public void onError(Exception ex) {
            handleDisconnect(this, "error", 0, ex.getMessage());
        }

        /**
         * Ends this client for good, whatever stage it has reached. Idempotent, so it is safe to
         * call from a callback of this same client.
         *
         * <p>An open client is closed gracefully, so the peer sees a proper close frame. The library
         * has no close-handshake timeout of its own, and the 30s connection-lost timer only catches
         * a peer that stops answering entirely, so a peer that keeps answering pings while never
         * answering the close could hold the transport open indefinitely. A one-shot watchdog
         * therefore drops the transport after {@link #CLOSE_HANDSHAKE_TIMEOUT_MILLIS} if the close
         * has not completed by then. A single compare-and-set on {@link #closeBounded} decides
         * whether the completing close or the watchdog acts, so the watchdog cannot touch the
         * transport once onClose has run even if it was already executing. It emits nothing - the
         * client is already detached and its teardown already reported - and runs at most once,
         * because kill() does.
         *
         * <p>A client that is not open has its transport dropped first, because closing the socket
         * is the only thing that actually stops bytes leaving; the library calls after it are
         * bookkeeping.
         *
         * <p>Must be called with CONNECT_LOCK released: closing takes the client's own monitor,
         * which its callback threads hold while they block on the lock. The watchdog takes no lock
         * either, and touches nothing but this client's transport.
         */
        void kill() {
            if (killed.getAndSet(true)) {
                return; // already ended, or being ended further up this same stack
            }

            if (isOpen()) {
                try {
                    close();
                } catch (Exception ignored) {}
                armCloseWatchdog();
                return;
            }

            // Drop the transport before anything else: it is what stops an upgrade request that has
            // been queued but not yet written, and what unblocks a connect thread parked in the
            // library's own I/O.
            closeTransport();

            try {
                close();
            } catch (Exception ignored) {}

            // close() reaches the engine only once the upgrade request has been written, so end the
            // connection outright as well. This delivers onClose synchronously on the calling
            // thread, which the identity check discards because every caller clears `ws` first; the
            // flag above stops that nested call from coming back through here.
            try {
                closeConnection(CloseFrame.ABNORMAL_CLOSE, "Connection superseded");
            } catch (Exception ignored) {}
        }

        /**
         * Bounds the graceful close started by {@link #kill()}. Harmless if the close beats it:
         * {@link #closeBounded} decides a single winner between this task and {@link #onClose},
         * so a task already running when the handshake completes still cannot act. Reading the
         * client's closed state instead would not be enough - the library invokes onClose before
         * it publishes CLOSED, so a check that passed could be stale by the next instruction.
         */
        private void armCloseWatchdog() {
            try {
                closeWatchdog = CLOSE_WATCHDOG.schedule(
                    () -> {
                        if (closeBounded.compareAndSet(false, true)) {
                            closeTransport();
                        }
                    },
                    CLOSE_HANDSHAKE_TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS
                );
            } catch (Exception ignored) {
                // The watchdog would not take the task, so bound the close the only other way
                // available: drop the transport now rather than leave it to the peer.
                if (closeBounded.compareAndSet(false, true)) {
                    closeTransport();
                }
            }
        }

        private void cancelCloseWatchdog() {
            // Claim the bound before cancelling. cancel(false) cannot stop a task that has already
            // started, so it is this flag, not the cancellation, that makes "the watchdog cannot
            // act once the close completed" true rather than merely likely.
            closeBounded.set(true);

            ScheduledFuture<?> pending = closeWatchdog;
            if (pending != null) {
                closeWatchdog = null;
                pending.cancel(false);
            }
        }

        /**
         * Publishes this attempt's socket and reports whether the attempt has been superseded,
         * closing the transport if so. Only ever called from the connect thread, which is the thread
         * that assigned the socket, so the value published here is never stale.
         */
        private boolean supersededAfterPublishingTransport() {
            transport = getSocket();

            if (!killed.get()) {
                return false;
            }

            closeTransport();
            return true;
        }

        private void closeTransport() {
            Socket socket = transport;
            if (socket == null) {
                // Best effort: before the connect thread republishes it, this read may still see
                // null. The kill flag, not this, is what guarantees the attempt goes no further.
                socket = getSocket();
            }

            if (socket == null) {
                return;
            }

            try {
                socket.close();
            } catch (Exception ignored) {}
        }

        private void setReadTimeout(int millis) {
            Socket socket = getSocket();
            if (socket == null) {
                return;
            }

            try {
                socket.setSoTimeout(millis);
            } catch (Exception ignored) {}
        }
    }
}
