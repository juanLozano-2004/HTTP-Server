package edu.eci.arep.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class HttpServerTest {

    @Test
    void rootPathMapsToIndex() {
        assertEquals("/index.html", HttpServer.normalizeRequestPath("/"));
    }

    @Test
    void normalPathIsUnchanged() {
        assertEquals("/app.js", HttpServer.normalizeRequestPath("/app.js"));
    }

    @Test
    void pathTraversalIsRejected() {
        assertNull(HttpServer.normalizeRequestPath("/../../etc/passwd"));
        assertNull(HttpServer.normalizeRequestPath("/images/../../../etc/passwd"));
    }

    @Test
    void contentTypesAreCorrect() {
        assertEquals("text/html; charset=utf-8", HttpServer.contentTypeFor("public/index.html"));
        assertEquals("application/javascript; charset=utf-8", HttpServer.contentTypeFor("public/app.js"));
        assertEquals("image/png", HttpServer.contentTypeFor("public/images/logo.png"));
        assertEquals("image/jpeg", HttpServer.contentTypeFor("public/images/photo.jpg"));
        assertEquals("application/octet-stream", HttpServer.contentTypeFor("public/unknown.xyz"));
    }
}