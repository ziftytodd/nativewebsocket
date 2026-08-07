package com.zifty.plugins.nativewebsocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * The strings under test are the ones Java-WebSocket 1.5.2 produces in
 * {@code Draft.translateHandshakeHttpClient}: {@code "Invalid status code received: %s Status line:
 * %s"}. They are reproduced verbatim here so a dependency bump that changes the wording shows up as
 * a failing test rather than as a silently missing httpStatus field.
 */
public class HandshakeStatusTest {

    @Test
    public void readsTheStatusOfARejectedUpgrade() {
        assertEquals(
            Integer.valueOf(401),
            HandshakeStatus.parse("Invalid status code received: 401 Status line: HTTP/1.1 401 Unauthorized")
        );
        assertEquals(
            Integer.valueOf(429),
            HandshakeStatus.parse("Invalid status code received: 429 Status line: HTTP/1.1 429 Too Many Requests")
        );
        assertEquals(
            Integer.valueOf(503),
            HandshakeStatus.parse("Invalid status code received: 503 Status line: HTTP/1.1 503 Service Unavailable")
        );
    }

    @Test
    public void ignoresTextThatCarriesNoStatus() {
        assertNull(HandshakeStatus.parse(null));
        assertNull(HandshakeStatus.parse(""));
        assertNull(HandshakeStatus.parse("Connection refused"));
        assertNull(HandshakeStatus.parse("draft Draft_6455 refuses handshake"));
        // A close reason quoting a websocket close code is not an HTTP status.
        assertNull(HandshakeStatus.parse("Connection closed with code 1013"));
    }

    @Test
    public void rejectsMalformedStatusTokens() {
        assertNull(HandshakeStatus.parse("Invalid status code received: 4010 Status line: garbage"));
        assertNull(HandshakeStatus.parse("Invalid status code received: HTTP/1.1 Status line: garbage"));
    }
}
