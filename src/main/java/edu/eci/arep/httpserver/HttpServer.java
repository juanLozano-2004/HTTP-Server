package edu.eci.arep.httpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class HttpServer {

    private static final String PUBLIC_PREFIX = "public";

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // Enlazado por defecto a todas las interfaces: necesario para
            // que EC2 acepte conexiones remotas, no solo localhost.
            System.out.println("Server listening on port " + port);
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    handleClient(clientSocket);
                } catch (IOException e) {
                    // Una conexión mal formada no debe tumbar el servidor.
                    System.err.println("Error handling connection: " + e.getMessage());
                }
            }
        }
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0) {
            return Integer.parseInt(args[0]);
        }
        String envPort = System.getenv("APP_PORT");
        if (envPort != null) {
            return Integer.parseInt(envPort);
        }
        return 35000;
    }

    private static void handleClient(Socket socket) throws IOException {
        socket.setSoTimeout(10000);
        InputStream rawIn = socket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.UTF_8));
        OutputStream out = socket.getOutputStream();

        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isBlank()) {
            return;
        }
        System.out.println("Request line: " + requestLine);

        // Consumimos y descartamos los headers; no los necesitamos en este lab.
        String header;
        while ((header = reader.readLine()) != null && !header.isEmpty()) {
            // no-op: solo vaciamos el stream
        }

        String[] parts = requestLine.split(" ");
        if (parts.length < 3) {
            sendPlainText(out, 400, "Bad Request");
            return;
        }
        String method = parts[0];
        String rawTarget = parts[1];

        if (!method.equals("GET")) {
            sendPlainText(out, 405, "Method Not Allowed");
            return;
        }

        String path;
        String query;
        int qIndex = rawTarget.indexOf('?');
        if (qIndex >= 0) {
            path = rawTarget.substring(0, qIndex);
            query = rawTarget.substring(qIndex + 1);
        } else {
            path = rawTarget;
            query = "";
        }
        Map<String, String> params = parseQuery(query);

        switch (path) {
            case "/api/greet" -> handleGreet(out, params);
            case "/api/square" -> handleSquare(out, params);
            case "/api/time" -> handleTime(out);
            case "/api/health" -> sendJson(out, 200, "{\"status\":\"ok\"}");
            default -> handleStatic(out, path);
        }
    }

    // ---------- Servicios hardcodeados ----------

    private static void handleGreet(OutputStream out, Map<String, String> params) throws IOException {
        String name = params.get("name");
        if (name == null || name.isBlank()) {
            sendJson(out, 400, "{\"error\":\"missing required parameter: name\"}");
            return;
        }
        String body = "{\"message\":\"Hello, " + escapeJson(name) + "!\"}";
        sendJson(out, 200, body);
    }

    private static void handleSquare(OutputStream out, Map<String, String> params) throws IOException {
        String value = params.get("value");
        if (value == null || value.isBlank()) {
            sendJson(out, 400, "{\"error\":\"missing required parameter: value\"}");
            return;
        }
        double number;
        try {
            number = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            sendJson(out, 400, "{\"error\":\"value must be numeric\"}");
            return;
        }
        double square = number * number;
        sendJson(out, 200, "{\"input\":" + number + ",\"square\":" + square + "}");
    }

    private static void handleTime(OutputStream out) throws IOException {
        sendJson(out, 200, "{\"serverTime\":\"" + Instant.now() + "\"}");
    }

    // ---------- Recursos estáticos ----------

    private static void handleStatic(OutputStream out, String requestPath) throws IOException {
        String normalized = normalizeRequestPath(requestPath);
        if (normalized == null) {
            sendPlainText(out, 400, "Bad Request");
            return;
        }

        String resourcePath = PUBLIC_PREFIX + normalized; // ej: "public/index.html"
        try (InputStream resourceStream = HttpServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (resourceStream == null) {
                sendPlainText(out, 404, "Not Found");
                return;
            }
            byte[] content = resourceStream.readAllBytes();
            sendBytes(out, 200, contentTypeFor(resourcePath), content);
        }
    }

    // Visibilidad de paquete (sin modificador) para poder probarlo con JUnit
    static String normalizeRequestPath(String requestPath) {
        String cleanPath = requestPath.equals("/") ? "/index.html" : requestPath;
        String normalized = Paths.get(cleanPath).normalize().toString().replace('\\', '/');
        if (normalized.contains("..")) {
            return null; // intento de path traversal
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    static String contentTypeFor(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        return "application/octet-stream";
    }

    // ---------- Utilidades ----------

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query.isBlank()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            try {
                key = URLDecoder.decode(key, StandardCharsets.UTF_8);
                value = URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
            map.put(key, value);
        }
        return map;
    }

    private static String escapeJson(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void sendJson(OutputStream out, int status, String jsonBody) throws IOException {
        sendBytes(out, status, "application/json; charset=utf-8", jsonBody.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendPlainText(OutputStream out, int status, String message) throws IOException {
        sendBytes(out, status, "text/plain; charset=utf-8", message.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(OutputStream out, int status, String contentType, byte[] body) throws IOException {
        String statusText = statusText(status);
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(status).append(" ").append(statusText).append("\r\n");
        headers.append("Content-Type: ").append(contentType).append("\r\n");
        headers.append("Content-Length: ").append(body.length).append("\r\n");
        headers.append("Connection: close\r\n");
        headers.append("\r\n");

        out.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private static String statusText(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "Error";
        };
    }
}