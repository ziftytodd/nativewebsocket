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
    
    public func didReceive(event: WebSocketEvent, client: WebSocket) {
        switch event {
        case .connected(let headers):
            isConnected = true
            self.notifyListeners("connected", data: [ "connected": true ])
            print("NWS: websocket is connected: \(headers)")
        case .disconnected(let reason, let code):
            isConnected = false
            print("NWS: websocket is disconnected: \(reason) with code: \(code)")
            self.notifyListeners("disconnected", data: [
                "disconnected": true,
                "reason": reason,
                "code": code
            ])
            socket = nil
            isConnected = false
        case .text(let string):
            print("NWS: Received text: \(string)")
            self.notifyListeners("message", data: [ "data": string, "binary": false ])
        case .binary(let data):
            print("NWS: Received binary: \(data.count)")
            self.notifyListeners("message", data: [ "data": data.base64EncodedString(), "binary": true ])
        case .ping(_):
            break
        case .pong(_):
            break
        case .viabilityChanged(_):
            break
        case .reconnectSuggested(_):
            break
        case .cancelled:
            isConnected = false
            socket = nil
            self.notifyListeners("disconnected", data: [ "reason": "cancelled" ])
        case .error(let error):
            isConnected = false
            socket = nil
            print("NWS: ERROR on socket error=\(error)")
            self.notifyListeners("disconnected", data: [
                "reason": "error",
                "error": "\(error)"
            ])
        }
    }
    
    
    @objc func connect(_ call: CAPPluginCall) {
        if (!isConnected) {
            print("NWS: Connecting to URL \(call.getString("url"))")
            var request = URLRequest(url: URL(string: call.getString("url")!)!)
            request.addValue("capacitor://localhost", forHTTPHeaderField: "Origin")
            request.timeoutInterval = 10
            socket = WebSocket(request: request)
            socket!.delegate = self
            socket!.connect()
            print("NWS: Connect started")
        }
        
        call.resolve()
    }

    @objc func send(_ call: CAPPluginCall) {
        if (isConnected) {
            if let sock = socket {
                sock.write(string: call.getString("message")!)
                call.resolve([ "sent": true ])
            } else {
                call.reject("Websocket not connected")
            }
        } else {
            call.reject("Websocket not connected")
        }
    }

    @objc func disconnect(_ call: CAPPluginCall) {
        if (isConnected) {
            if let sock = socket {
                sock.disconnect()
                call.resolve([ "disconnected": true ])
                isConnected = false
                socket = nil
            } else {
                call.reject("Websocket not connected")
            }
        } else {
            call.reject("Websocket not connected")
        }
    }
}
