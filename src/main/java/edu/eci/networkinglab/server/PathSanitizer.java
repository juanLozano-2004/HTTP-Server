package edu.eci.networkinglab.server;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

public final class PathSanitizer {

    private PathSanitizer() {
    }

    public static final class UnsafePathException extends RuntimeException {
        public UnsafePathException(String message) {
            super(message);
        }
    }

    public static String sanitize(String rawPath) {
        String decoded = decode(rawPath);

        Deque<String> stack = new ArrayDeque<>();
        for (String segment : decoded.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (stack.isEmpty()) {
                    throw new UnsafePathException("Path traversal attempt: " + rawPath);
                }
                stack.removeLast();
            } else {
                stack.addLast(segment);
            }
        }
        return String.join("/", stack);
    }

    private static String decode(String rawPath) {
        try {
            return URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is always supported; this branch is unreachable.
            throw new IllegalStateException(e);
        }
    }
}
