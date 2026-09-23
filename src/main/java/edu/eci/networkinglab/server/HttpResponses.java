package edu.eci.networkinglab.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class HttpResponses {

    private HttpResponses() {
    }

    public static void send(OutputStream out, int status, String reason, String contentType, byte[] body) throws IOException {
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        headers.append("Content-Type: ").append(contentType).append("\r\n");
        headers.append("Content-Length: ").append(body.length).append("\r\n");
        headers.append("Connection: close\r\n");
        headers.append("Server: eci-networking-lab-minimal-server\r\n");
        headers.append("\r\n");

        out.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    public static void sendText(OutputStream out, int status, String reason, String contentType, String body) throws IOException {
        send(out, status, reason, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    public static void sendJson(OutputStream out, int status, String reason, String json) throws IOException {
        sendText(out, status, reason, "application/json; charset=utf-8", json);
    }

    public static void sendPlainText(OutputStream out, int status, String reason, String message) throws IOException {
        sendText(out, status, reason, "text/plain; charset=utf-8", message);
    }
}
