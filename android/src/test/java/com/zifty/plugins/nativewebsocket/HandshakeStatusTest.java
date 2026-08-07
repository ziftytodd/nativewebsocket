package com.zifty.plugins.nativewebsocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.java_websocket.drafts.Draft;
import org.java_websocket.enums.Role;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.junit.Test;

public class HandshakeStatusTest {

    /**
     * Drives real rejected upgrade responses through Java-WebSocket's own parser rather than
     * asserting on a transcription of its wording. A dependency bump that reworded
     * {@code Draft.translateHandshakeHttpClient} would fail here, instead of leaving these tests
     * green while production quietly stopped reporting httpStatus.
     */
    @Test
    public void readsTheStatusJavaWebsocketActuallyReports() {
        assertEquals(Integer.valueOf(401), statusOfRejectedUpgrade("HTTP/1.1 401 Unauthorized"));
        assertEquals(Integer.valueOf(429), statusOfRejectedUpgrade("HTTP/1.1 429 Too Many Requests"));
        assertEquals(Integer.valueOf(503), statusOfRejectedUpgrade("HTTP/1.1 503 Service Unavailable"));
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
    public void rejectsAnythingOutsideTheLibrarysExactFormat() {
        // Malformed status token: three digits are present but the format does not hold.
        assertNull(HandshakeStatus.parse("Invalid status code received: 401x Status line: HTTP/1.1 401x Nope"));
        assertNull(HandshakeStatus.parse("Invalid status code received: 4010 Status line: HTTP/1.1 4010 Nope"));
        assertNull(HandshakeStatus.parse("Invalid status code received: HTTP/1.1 Status line: garbage"));
        // Truncated: no status line follows.
        assertNull(HandshakeStatus.parse("Invalid status code received: 401 Status line: "));
        // A server's own close reason that merely quotes the wording must not be mistaken for one.
        assertNull(HandshakeStatus.parse("Rejected: Invalid status code received: 401 Status line: HTTP/1.1 401 Unauthorized"));
    }

    /**
     * The plugin sees this text as the close reason: the library throws InvalidHandshakeException
     * out of the draft, and WebSocketImpl passes its message straight through to onClose.
     */
    private static Integer statusOfRejectedUpgrade(String statusLine) {
        String response = statusLine + "\r\nServer: test\r\nContent-Length: 0\r\n\r\n";
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.US_ASCII));

        try {
            Draft.translateHandshakeHttp(buffer, Role.CLIENT);
        } catch (InvalidHandshakeException e) {
            return HandshakeStatus.parse(e.getMessage());
        }

        fail("Java-WebSocket accepted \"" + statusLine + "\" as an upgrade response");
        return null;
    }
}
