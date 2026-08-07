import Foundation
import Capacitor
import Starscream

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(NativeWebsocketPlugin)
public class NativeWebsocketPlugin: CAPPlugin, CAPBridgedPlugin, Starscream.WebSocketDelegate {
    public typealias WS = Starscream.WebSocket
    public typealias WSEvent = Starscream.WebSocketEvent

    public let identifier = "NativeWebsocketPlugin"
    public let jsName = "NativeWebsocket"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "connect", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "send", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "disconnect", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isConnected", returnType: CAPPluginReturnPromise)
    ]

    /// How long a `connect()` may stay in flight before a later `connect()` is allowed to replace it.
    private static let connectTimeout: TimeInterval = 30
    /// How often a ping is written while the socket is open.
    private static let pingInterval: TimeInterval = 30
    /// How long the socket may go without receiving any frame before it is considered dead.
    private static let frameDeadline: TimeInterval = 90
    /// RFC 6455 "abnormal closure", reported when the keepalive deadline expires.
    private static let abnormalCloseCode = 1006

    /// Every elapsed-time decision reads this instead of the wall clock, which jumps on NTP
    /// corrections and manual date changes. It reads `DispatchTime` specifically, the clock
    /// `DispatchSourceTimer` schedules against, so a tick and the deadline it enforces are
    /// measured against one source by construction rather than by assumption.
    ///
    /// It counts awake time only: waking from a long sleep therefore does not by itself trip the
    /// 90s deadline, because neither this clock nor the timer advanced while the device slept.
    /// The socket is usually dead by then, and that surfaces the ordinary way - the next ping
    /// fails, or the app resyncs on resume via `isConnected()` - rather than as a timeout for
    /// time the plugin never actually spent waiting.
    private static var monotonicNow: TimeInterval {
        return TimeInterval(DispatchTime.now().uptimeNanoseconds) / 1_000_000_000
    }

    let connectQueue = DispatchQueue(label: "Connect Queue")

    // Everything below is confined to `connectQueue`. Every socket we create gets its
    // `callbackQueue` set to `connectQueue`, so Starscream's delegate callbacks land there too
    // instead of on the main queue.
    var socket: WS?
    var socketIsOpen: Bool = false
    var connecting: Bool = false
    var connectTimeoutAt: TimeInterval = 0
    /// Generation of `socket`, or 0 when there is no socket.
    private var socketGeneration: UInt64 = 0
    /// Monotonic counter; every socket we create is tagged with the next value.
    private var generationCounter: UInt64 = 0
    /// Highest generation that has already produced its `disconnected` event.
    private var settledGeneration: UInt64 = 0
    /// Generation that has already produced its `connected` event.
    private var connectedGeneration: UInt64 = 0
    private var keepaliveTimer: DispatchSourceTimer?
    private var lastFrameAt: TimeInterval = 0

    deinit {
        keepaliveTimer?.cancel()
    }

    public func didReceive(event: WSEvent, client: Starscream.WebSocketClient) {
        // Delivered on `connectQueue`; see `callbackQueue` in `connect`.
        guard let generation = currentGeneration(of: client) else {
            return
        }

        switch event {
        // Frame payloads and handshake headers routinely carry auth tokens and session cookies,
        // and the device log outlives the app, so only their shape is logged.
        case .connected(let headers):
            print("NWS: websocket is connected with \(headers.count) header(s)")
            handleConnect(generation: generation)
        case .disconnected(let reason, let code):
            print("NWS: websocket is disconnected: \(reason) with code: \(code)")
            handleDisconnect(generation: generation, reason: reason, code: Int(code))
        case .text(let string):
            noteFrameReceived()
            print("NWS: Received text: \(string.count) chars")
            emit("message", [ "data": string, "binary": false ])
        case .binary(let data):
            noteFrameReceived()
            print("NWS: Received binary: \(data.count)")
            emit("message", [ "data": data.base64EncodedString(), "binary": true ])
        case .ping:
            noteFrameReceived()
        case .pong:
            noteFrameReceived()
        case .viabilityChanged:
            break
        case .reconnectSuggested:
            break
        case .cancelled:
            handleDisconnect(generation: generation, reason: "cancelled", code: 0)
        case .error(let error):
            print("NWS: ERROR on socket error=\(Self.redacted(error))")
            handleDisconnect(generation: generation, reason: "disconnected", code: 0, error: error)
        case .peerClosed(let error):
            handleDisconnect(generation: generation, reason: "peerClosed", code: 0, error: error)
        }
    }

    // MARK: - Socket identity

    /// Resolves the socket a callback came from to its generation, or nil when that socket has
    /// already been abandoned. A late callback from an abandoned socket must never touch current
    /// state, so the stale socket is detached and stopped on sight and the event is dropped.
    private func currentGeneration(of client: Starscream.WebSocketClient) -> UInt64? {
        if let sock = socket, client === sock {
            return socketGeneration
        }

        print("NWS: Ignoring callback from an abandoned socket")
        detach(client as? WS, graceful: false)
        return nil
    }

    /// Detaches our handlers from a socket and stops it.
    ///
    /// Starscream's `disconnect()` writes a close frame, which its engine silently drops while the
    /// socket is still connecting - the transport would stay up. Only a socket we know is open gets
    /// the graceful close; anything else is stopped outright.
    private func detach(_ sock: WS?, graceful: Bool) {
        guard let sock = sock else {
            return
        }

        sock.delegate = nil
        sock.onEvent = nil
        if graceful {
            sock.disconnect()
        } else {
            sock.forceDisconnect()
        }
    }

    // MARK: - Terminal events

    private func handleConnect(generation: UInt64) {
        noteFrameReceived()
        socketIsOpen = true
        connecting = false
        connectTimeoutAt = 0
        startKeepalive(generation: generation)

        guard connectedGeneration != generation else {
            return
        }
        connectedGeneration = generation
        emit("connected", [ "connected": true ])
    }

    /// The single funnel for every terminal socket event. Emits exactly one `disconnected` per
    /// socket generation: a generation that has already reported is dropped, and a generation that
    /// has not reports even when the socket reference is already gone.
    private func handleDisconnect(generation: UInt64, reason: String, code: Int, error: Error? = nil) {
        guard generation > settledGeneration else {
            print("NWS: Ignoring a repeat terminal event for generation \(generation)")
            return
        }

        // No placeholder when there is no error: a clean close, a cancellation and a keepalive
        // timeout all carry none, and fabricating one made every close look like a failure.
        // `emitDisconnected` omits the key, which is what Android has always done.
        let message = error.map { "\($0)" }
        let status = httpStatus(from: error)
        teardownSocket()
        emitDisconnected(reason: reason, code: code, error: message, httpStatus: status)
    }

    /// Tears the socket down whatever state it is in, then always notifies JS.
    private func forceDisconnect(reason: String) {
        print("NWS: Forcing disconnect")
        teardownSocket()
        emitDisconnected(reason: reason, code: -1, error: nil, httpStatus: nil)
    }

    /// Stops keepalive, detaches and closes the socket, clears the refs and retires its generation.
    /// Safe to call when there is no socket.
    private func teardownSocket() {
        stopKeepalive()
        detach(socket, graceful: socketIsOpen)
        socket = nil
        socketIsOpen = false
        connecting = false
        connectTimeoutAt = 0
        if socketGeneration > settledGeneration {
            settledGeneration = socketGeneration
        }
        socketGeneration = 0
    }

    /// An error's description renders its associated values, and `notAnUpgrade` carries the whole
    /// response header dictionary, so the log gets the error's shape rather than its contents.
    private static func redacted(_ error: Error?) -> String {
        guard let error = error else {
            return "none"
        }
        if let upgradeError = error as? HTTPUpgradeError, case .notAnUpgrade(let status, let headers) = upgradeError {
            return "HTTPUpgradeError.notAnUpgrade httpStatus=\(status) with \(headers.count) header(s)"
        }
        return "\(type(of: error))"
    }

    /// Starscream reports a failed HTTP upgrade as `HTTPUpgradeError.notAnUpgrade(status, headers)`.
    /// Any other error carries no status, so the field is omitted.
    private func httpStatus(from error: Error?) -> Int? {
        guard let upgradeError = error as? HTTPUpgradeError else {
            return nil
        }
        if case .notAnUpgrade(let status, _) = upgradeError {
            return status
        }
        return nil
    }

    private func emitDisconnected(reason: String, code: Int, error: String?, httpStatus: Int?) {
        var data: [String: Any] = [
            "disconnected": true,
            "reason": reason,
            "code": code
        ]
        if let error = error {
            data["error"] = error
        }
        if let httpStatus = httpStatus {
            data["httpStatus"] = httpStatus
        }
        emit("disconnected", data)
    }

    /// Events are retained until consumed so anything fired before JS registers a listener is still
    /// delivered on registration. Capacitor's listener bookkeeping is not thread safe, so emission
    /// hops to the main queue; `connectQueue` is serial, which keeps events in order.
    private func emit(_ eventName: String, _ data: [String: Any]) {
        DispatchQueue.main.async {
            self.notifyListeners(eventName, data: data, retainUntilConsumed: true)
        }
    }

    // MARK: - Keepalive

    private func noteFrameReceived() {
        lastFrameAt = Self.monotonicNow
    }

    private func startKeepalive(generation: UInt64) {
        stopKeepalive()

        let timer = DispatchSource.makeTimerSource(queue: connectQueue)
        timer.schedule(deadline: .now() + Self.pingInterval, repeating: Self.pingInterval)
        timer.setEventHandler { [weak self] in
            self?.keepaliveTick(generation: generation)
        }
        keepaliveTimer = timer
        timer.resume()
    }

    private func stopKeepalive() {
        keepaliveTimer?.cancel()
        keepaliveTimer = nil
    }

    private func keepaliveTick(generation: UInt64) {
        guard generation == socketGeneration, socketIsOpen, let sock = socket else {
            stopKeepalive()
            return
        }

        if Self.monotonicNow - lastFrameAt > Self.frameDeadline {
            print("NWS: No frame received for over \(Int(Self.frameDeadline))s, treating the socket as dead")
            handleDisconnect(generation: generation, reason: "keepalive-timeout", code: Self.abnormalCloseCode)
            return
        }

        sock.write(ping: Data())
    }

    // MARK: - Plugin methods

    @objc public func connect(_ call: CAPPluginCall) {
        guard let urlString = call.getString("url"), let url = URL(string: urlString),
              let scheme = url.scheme, let host = url.host else {
            call.reject("A valid 'url' is required to connect")
            return
        }

        connectQueue.async {
            print("NWS: Starting connect")

            if self.socketIsOpen {
                print("NWS: Already connected")
                call.resolve([ "result": "Already Connected" ])
                return
            }

            let now = Self.monotonicNow
            if self.connecting && self.connectTimeoutAt > now {
                print("NWS: Already trying to connect")
                call.resolve([ "result": "Already trying to connect" ])
                return
            }

            // The in-flight guard expired. Close whatever it left behind instead of abandoning it,
            // so its late callbacks can never act on the socket we are about to create. Silently:
            // that attempt never opened and JS asked for this replacement itself, so a
            // `disconnected` here would only confuse the app's reconnect handling.
            if self.socket != nil {
                print("NWS: Replacing a socket left over from an earlier connect")
                self.teardownSocket()
            }

            self.generationCounter += 1
            let generation = self.generationCounter

            // Scheme and host only: the path and query can carry an auth token.
            print("NWS: Connecting to \(scheme)://\(host)")
            var request = URLRequest(url: url)
            request.addValue("capacitor://localhost", forHTTPHeaderField: "Origin")
            request.timeoutInterval = 10

            let sock = WS(request: request)
            sock.callbackQueue = self.connectQueue
            sock.delegate = self
            self.socket = sock
            self.socketGeneration = generation
            self.connecting = true
            self.connectTimeoutAt = now + Self.connectTimeout
            sock.connect()
            print("NWS: Connect started")

            call.resolve([ "result": "Connection Starting" ])
        }
    }

    @objc public func send(_ call: CAPPluginCall) {
        guard let message = call.getString("message") else {
            call.reject("A 'message' is required to send")
            return
        }

        connectQueue.async {
            guard self.socketIsOpen, let sock = self.socket else {
                self.forceDisconnect(reason: "Websocket not connected")
                call.reject("Websocket not connected")
                return
            }

            sock.write(string: message)
            call.resolve([ "sent": true ])
        }
    }

    @objc public func disconnect(_ call: CAPPluginCall) {
        connectQueue.async {
            print("NWS: Starting disconnect")
            self.forceDisconnect(reason: "Called Disconnect")
            call.resolve([ "disconnected": true ])
        }
    }

    @objc public func isConnected(_ call: CAPPluginCall) {
        connectQueue.async {
            call.resolve([ "connected": self.socketIsOpen && self.socket != nil ])
        }
    }
}
