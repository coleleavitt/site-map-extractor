package burp.util;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.responses.HttpResponse;

public final class FileUtils {

    private FileUtils() {
    }

    public static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "_";
        String safe = s.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", "_");
        if (safe.length() > 100) safe = safe.substring(0, 100);
        return safe.isEmpty() ? "_" : safe;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    public static String categorizeContentType(HttpResponse response) {
        String ct = response.headers().stream()
                .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
                .map(HttpHeader::value)
                .findFirst()
                .orElse("");

        ct = ct.toLowerCase();
        if (ct.contains("javascript")) return "JS";
        if (ct.contains("css")) return "CSS";
        if (ct.contains("html")) return "HTML";
        if (ct.contains("json")) return "JSON";
        if (ct.contains("image/")) return "Image";
        if (ct.contains("font/") || ct.contains("woff") || ct.contains("ttf") || ct.contains("otf")) return "Font";
        return "Other";
    }

    public static String getExtensionForContentType(HttpResponse response) {
        String ct = response.headers().stream()
                .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
                .map(HttpHeader::value)
                .findFirst()
                .orElse("");

        int semi = ct.indexOf(';');
        if (semi > 0) ct = ct.substring(0, semi);
        ct = ct.trim().toLowerCase();

        return switch (ct) {
            case "text/html" -> ".html";
            case "text/css" -> ".css";
            case "text/javascript", "application/javascript", "application/x-javascript" -> ".js";
            case "application/json" -> ".json";
            case "application/xml", "text/xml" -> ".xml";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            case "image/webp" -> ".webp";
            case "font/woff" -> ".woff";
            case "font/woff2" -> ".woff2";
            case "application/pdf" -> ".pdf";
            case "text/plain" -> ".txt";
            default -> null;
        };
    }
}
