package com.zifty.plugins.nativewebsocket;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.java_websocket.client.WebSocketClient;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.Base64;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

@CapacitorPlugin(name = "NativeWebsocket")
public class NativeWebsocketPlugin extends Plugin {
    private boolean isConnected;
    private WebSocketClient ws;

    private static String toBase64String(ByteBuffer buff) {
        ByteBuffer bb = buff.asReadOnlyBuffer();
        bb.position(0);
        byte[] b = new byte[bb.limit()];
        bb.get(b, 0, b.length);
        return Base64.getEncoder().encodeToString(b);
    }

    @PluginMethod
    public void connect(PluginCall call) {
        if (isConnected) {
            try {
                if (ws != null) ws.close();
            } catch (Exception ignored) {}
            isConnected = false;
            ws = null;
        }

        String url = call.getString("url");
        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", "capacitor://localhost");

        try {
            ws = new WebSocketClient(new URI(url), headers) {
                @Override
                public void onMessage(String message) {
                    JSObject ret = new JSObject();
                    ret.put("data", message);
                    ret.put("binary", false);
                    notifyListeners("message", ret);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    JSObject ret = new JSObject();
                    ret.put("data", NativeWebsocketPlugin.toBase64String(bytes));
                    ret.put("binary", true);
                    notifyListeners("message", ret);
                }

                @Override
                public void onOpen(ServerHandshake handshake) {
                    isConnected = true;

                    JSObject ret = new JSObject();
                    ret.put("connected", true);
                    notifyListeners("connected", ret);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    handleDisconnect(reason, code, null);
                }

                @Override
                public void onError(Exception ex) {
                    handleDisconnect("error", 0, ex.getMessage());
                }
            };

            ws.connect();

            call.resolve(new JSObject());
        } catch (Exception e) {
            call.reject("Exception occurred: " + e.getMessage());
        }
    }

    private void handleDisconnect(String reason, int code, String error) {
        if (isConnected || (ws != null)) {
            if (ws != null) {
                try {
                    ws.close();
                } catch (Exception ignored) {}
            }

            JSObject ret = new JSObject();
            ret.put("disconnected", true);
            if (reason != null) ret.put("reason", reason);
            ret.put("code", code);
            if (error != null) ret.put("error", error);
            notifyListeners("disconnected", ret);
        }

        isConnected = false;
        ws = null;
    }

    @PluginMethod
    public void send(PluginCall call) {
        if (isConnected && (ws != null)) {
            try {
                ws.send(call.getString("message"));
                JSObject ret = new JSObject();
                ret.put("sent", true);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Exception occurred: " + e.getMessage());
            }
        } else {
            call.reject("Websocket not connected");
        }
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        if (isConnected || (ws != null)) {
            if (ws != null) {
                try {
                    ws.close();
                } catch (Exception ignored) {}
            }
        }

        isConnected = false;
        ws = null;

        JSObject ret = new JSObject();
        ret.put("disconnected", true);
        ret.put("reason", "Called disconnect");
        ret.put("code", -1);
        notifyListeners("disconnected", ret);

        call.resolve(new JSObject());
    }
}
