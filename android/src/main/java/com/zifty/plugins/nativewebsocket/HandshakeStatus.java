package com.zifty.plugins.nativewebsocket;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recovers the HTTP status of a rejected WebSocket upgrade handshake.
 *
 * <p>The obvious route - overriding {@code onWebsocketHandshakeReceivedAsClient} and reading
 * {@code ServerHandshake.getHttpStatus()} - only covers the successful handshake. Java-WebSocket
 * 1.5.2 refuses a non-101 upgrade response inside {@code Draft.translateHandshakeHttpClient},
 * before it has built a {@code ServerHandshake} at all, and {@code WebSocketImpl.decodeHandshake}
 * only reaches that callback once the draft has returned {@code MATCHED}. A rejected handshake
 * therefore never gets there.
 *
 * <p>What does survive is the {@code InvalidHandshakeException} message, which the library formats
 * as {@code "Invalid status code received: %s Status line: %s"} and delivers as the close reason.
 * Reading the status back out of it is the only way to surface a 401 or a 429 on this version of
 * the library. That makes this parser deliberately best-effort: text it does not recognise yields
 * {@code null} and the caller omits the field rather than guessing.
 */
final class HandshakeStatus {

    private static final Pattern INVALID_STATUS = Pattern.compile("Invalid status code received:\\s*(\\d{3})(?!\\d)");

    private HandshakeStatus() {}

    /**
     * @param text a close reason or error message, possibly null
     * @return the HTTP status the server answered the upgrade with, or null if the text carries none
     */
    static Integer parse(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = INVALID_STATUS.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        return Integer.valueOf(matcher.group(1));
    }
}
