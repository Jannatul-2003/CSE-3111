import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class FileClient {

    private static final String SERVER_URL = "http://localhost:8080";
    private static final Path DOWNLOAD_DIR = Paths.get("downloads");
    private static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n--- HTTP File Client ---");
                System.out.println("1. Upload File");
                System.out.println("2. Download File");
                System.out.println("3. List Files on Server");
                System.out.println("4. Exit");
                System.out.print("Choose an option: ");
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        System.out.print("Enter path of file to upload: ");
                        uploadFile(scanner.nextLine());
                        break;
                    case "2":
                        System.out.print("Enter filename to download: ");
                        downloadFile(scanner.nextLine());
                        break;
                    case "3":
                        try {
                            listFiles();
                        } catch (IOException e) {
                            System.out.println("Error listing files: " + e.getMessage());
                        }
                        break;
                    case "4":
                        System.out.println("Exiting...");
                        return;
                    default:
                        System.out.println("Invalid choice. Try again.");
                }
            }
        }
    }

    private static void uploadFile(String filePathStr) {
        Path filePath = Paths.get(filePathStr);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            System.out.println("File does not exist or is not valid.");
            return;
        }

        String filename = filePath.getFileName().toString();
        try {
            URL url = URI.create(SERVER_URL + "/upload?filename=" + URLEncoder.encode(filename, "UTF-8")).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/octet-stream");

            long fileSize = Files.size(filePath);
            System.out.println("Uploading: " + filename + " (" + formatFileSize(fileSize) + ")");

            try (InputStream fis = Files.newInputStream(filePath);
                 OutputStream os = conn.getOutputStream()) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalSent = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;
                    int progress = (int) ((totalSent * 100) / fileSize);
                    System.out.print("\rProgress: " + progress + "%");
                }

                os.flush();
                System.out.println("\nUpload complete.");
            }

            printServerResponse(conn);
        } catch (IOException e) {
            System.out.println("Upload failed: " + e.getMessage());
        }
    }

    private static void downloadFile(String filename) {
        try {
            URL url = URI.create(SERVER_URL + "/download?filename=" + URLEncoder.encode(filename, "UTF-8")).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                if (!Files.exists(DOWNLOAD_DIR)) {
                    Files.createDirectories(DOWNLOAD_DIR);
                }

                Path targetFile = getUniqueDownloadPath(filename);
                long contentLength = conn.getContentLengthLong();
                System.out.println("Downloading: " + filename +
                        (contentLength > 0 ? " (" + formatFileSize(contentLength) + ")" : ""));

                try (InputStream is = conn.getInputStream();
                     OutputStream os = Files.newOutputStream(targetFile)) {

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    long totalRead = 0;

                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        if (contentLength > 0) {
                            int progress = (int) ((totalRead * 100) / contentLength);
                            System.out.print("\rProgress: " + progress + "%");
                        } else {
                            System.out.print("\rDownloaded: " + totalRead + " bytes");
                        }
                    }
                    System.out.println("\nDownload completed: " + targetFile.toAbsolutePath());
                }
            } else if (responseCode == 404) {
                System.out.println("File not found on server.");
            } else {
                System.out.println("Failed with HTTP code: " + responseCode);
                printServerResponse(conn);
            }

        } catch (IOException e) {
            System.out.println("Download failed: " + e.getMessage());
        }
    }
    private static void listFiles() throws IOException {
        URL url = URI.create(SERVER_URL + "/list").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                System.out.println("Files on server:");
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println(" - " + line);
                }
            }
        } else {
            System.out.println("Failed to retrieve file list. Server responded with: " + responseCode);
        }
    }

    private static Path getUniqueDownloadPath(String filename) {
        Path target = DOWNLOAD_DIR.resolve(filename);
        int count = 1;
        String baseName = filename;
        String extension = "";

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex != -1) {
            baseName = filename.substring(0, dotIndex);
            extension = filename.substring(dotIndex);
        }

        while (Files.exists(target)) {
            target = DOWNLOAD_DIR.resolve(baseName + "_" + count + extension);
            count++;
        }
        return target;
    }

    private static void printServerResponse(HttpURLConnection conn) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            System.out.println("Server says:");
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                String line;
                System.out.println("Server error:");
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException ex) {
                System.out.println("Unable to read server response.");
            }
        }
    }

    private static String formatFileSize(long size) {
        if (size < 1024) return size + " bytes";
        else if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        else if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        else return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }
}

