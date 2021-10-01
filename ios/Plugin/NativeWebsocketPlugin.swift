import Foundation
import Capacitor
import Starscream

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(NativeWebsocketPlugin)
public class NativeWebsocketPlugin: CAPPlugin, WebSocketDelegate {
    var socket: WebSocket?
    var isConnected: Bool = false

    public func didReceive(event: WebSocketEvent, client: WebSocketClient) {
        switch event {
        case .connected(let headers):
            isConnected = true
            self.notifyListeners("connected", data: [ "connected": true ])
            print("NWS: websocket is connected: \(headers)")
            break
        case .disconnected(let reason, let code):
            isConnected = false
            print("NWS: websocket is disconnected: \(reason) with code: \(code)")
            handleDisconnect(reason: reason, code: code, error: nil)
            break
        case .text(let string):
            print("NWS: Received text: \(string)")
            self.notifyListeners("message", data: [ "data": string, "binary": false ])
            break
        case .binary(let data):
            print("NWS: Received binary: \(data.count)")
            self.notifyListeners("message", data: [ "data": data.base64EncodedString(), "binary": true ])
            break
        case .ping(_):
            break
        case .pong(_):
            break
        case .viabilityChanged(_):
            break
        case .reconnectSuggested(_):
            break
        case .cancelled:
            handleDisconnect(reason: "cancelled", code: 0, error: nil)
            break
        case .error(let error):
            print("NWS: ERROR on socket error=\(error)")
            handleDisconnect(reason: "disconnected", code: 0, error: "\(error)")

            socket?.delegate = nil
            socket?.disconnect()
            socket = nil
            self.notifyListeners("disconnected", data: [
                "reason": "error",
                "error": "\(error)"
            ])
            break
        }
    }

    func handleDisconnect(reason: String, code: UInt16, error: String?) {
        if let sock = socket {
            self.notifyListeners("disconnected", data: [
                "disconnected": true,
                "reason": reason,
                "code": code,
                "error": error
            ])
            sock.delegate = nil
            sock.disconnect()
        }
        socket = nil
        isConnected = false
    }

    @objc func connect(_ call: CAPPluginCall) {
        print("NWS: Starting connect")

        if (isConnected) {
            if let sock = socket {
                sock.disconnect()
                isConnected = false
                socket?.delegate = nil
                socket = nil
            }
        }

        print("NWS: Connecting to URL \(call.getString("url"))")
        var request = URLRequest(url: URL(string: call.getString("url")!)!)
        request.addValue("capacitor://localhost", forHTTPHeaderField: "Origin")
        request.timeoutInterval = 10
        socket = WebSocket(request: request)
        socket!.delegate = self
        socket!.connect()
        print("NWS: Connect started")

        call.resolve()
    }

    @objc func send(_ call: CAPPluginCall) {
        if (isConnected) {
            if let sock = socket {
                sock.write(string: call.getString("message")!)
                call.resolve([ "sent": true ])
            } else {
                forceDisconnect()
                call.reject("Websocket not connected")
            }
        } else {
            forceDisconnect()
            call.reject("Websocket not connected")
        }
    }

    @objc func disconnect(_ call: CAPPluginCall) {
        print("NWS: Starting disconnect")
        forceDisconnect()
        call.resolve([ "disconnected": true ])
    }

    func forceDisconnect() {
        print("NWS: Forcing disconnect")
        if (isConnected) {
            if let sock = socket {
                sock.disconnect()
            }

            socket?.delegate = nil
            socket = nil
            isConnected = false
        }

        self.notifyListeners("disconnected", data: [
            "disconnected": true,
            "reason": "Called disconnect",
            "code": -1
        ])
    }
}
