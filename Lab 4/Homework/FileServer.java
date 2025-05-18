import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

public class FileServer {

    private static final int PORT = 8080;
    private static final File UPLOAD_DIR = new File("uploads");
    private static final int MAX_UPLOAD_SIZE = 20 * 1024 * 1024; // 20MB max

    public static void main(String[] args) throws IOException {
        if (!UPLOAD_DIR.exists())
            UPLOAD_DIR.mkdirs();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/upload", new UploadHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/list", new ListFilesHandler());
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("Secure file server started on port " + PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            System.out.println("Server stopped.");
        }));

    }

    static class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            String filename = params.get("filename");
            if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                sendResponse(exchange, 400, "Invalid filename");
                return;
            }

            File file = new File(UPLOAD_DIR, filename);
            if (!file.exists() || file.isDirectory()) {
                sendResponse(exchange, 404, "File Not Found");
                return;
            }

            String mime = java.nio.file.Files.probeContentType(file.toPath());
            if (mime == null)
                mime = "application/octet-stream";
            exchange.getResponseHeaders().add("Content-Type", mime);
            exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, file.length());

            try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(file)) {
                fis.transferTo(os);
            }
        }
    }

    static class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            // Enforce max upload size
            InputStream is = exchange.getRequestBody();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                total += bytesRead;
                if (total > MAX_UPLOAD_SIZE) {
                    sendResponse(exchange, 413, "Payload Too Large");
                    return;
                }
                baos.write(buffer, 0, bytesRead);
            }

            byte[] uploadedData = baos.toByteArray();

            // Determine filename
            String filename = exchange.getRequestHeaders().getFirst("X-Filename") != null
                    ? exchange.getRequestHeaders().getFirst("X-Filename")
                    : parseQuery(exchange.getRequestURI().getRawQuery()).get("filename");
            if (filename == null || filename.isBlank()) {
                filename = "upload_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".dat";
            } else {
                if (filename.matches(".*[<>:\"|?*].*")) {
                    sendResponse(exchange, 400, "Invalid characters in filename");
                    return;
                }
            }
            filename = filename.replaceAll("[\\\\/]", "_");

            File uploadedFile = new File(UPLOAD_DIR, filename);
            String uploadedHash = computeSHA256(new ByteArrayInputStream(uploadedData));

            if (uploadedFile.exists()) {
                String existingHash = computeSHA256(new FileInputStream(uploadedFile));
                if (uploadedHash.equals(existingHash)) {
                    sendResponse(exchange, 200, "Duplicate file detected. Upload skipped.");
                    return;
                } else {
                    String baseName = filename;
                    String extension = "";
                    int dotIndex = filename.lastIndexOf('.');
                    if (dotIndex != -1) {
                        baseName = filename.substring(0, dotIndex);
                        extension = filename.substring(dotIndex);
                    }

                    int count = 1;
                    while (uploadedFile.exists()) {
                        String newName = baseName + "_" + count + extension;
                        uploadedFile = new File(UPLOAD_DIR, newName);
                        count++;
                    }
                }

            }

            try (FileOutputStream fos = new FileOutputStream(uploadedFile)) {
                fos.write(uploadedData);
            }

            sendResponse(exchange, 200, "Upload successful: " + uploadedFile.getName());
        }
    }

    static class ListFilesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            File[] files = UPLOAD_DIR.listFiles();
            if (files == null) {
                exchange.sendResponseHeaders(500, -1);
                return;
            }

            StringBuilder response = new StringBuilder();
            for (File file : files) {
                if (file.isFile()) {
                    response.append(file.getName()).append(" ("+formatFileSize(file.length())+")").append("\n");
                }
            }

            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    // === Utility Methods ===

    private static String formatFileSize(long size) {
        if (size < 1024) return size + " bytes";
        else if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        else if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        else return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }
    private static void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        byte[] response = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        // Security headers
        exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().add("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().add("X-XSS-Protection", "1; mode=block");

        exchange.sendResponseHeaders(code, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static Map<String, String> parseQuery(String query) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        if (query == null)
            return map;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                String val = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                map.put(key, val);
            }
        }
        return map;
    }

    private static String computeSHA256(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : hash)
                hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not supported", e);
        }
    }
}

