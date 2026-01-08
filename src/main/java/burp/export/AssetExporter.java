package burp.export;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import burp.model.SiteMapEntry;
import burp.util.FileUtils;
import burp.util.JsonUtils;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssetExporter {

    private final Logging logging;

    public AssetExporter(Logging logging) {
        this.logging = logging;
    }

    public int export(List<SiteMapEntry> entries, Path assetsPath, Path metaPath, Runnable onProgress) throws Exception {
        int count = 0;
        Map<String, Integer> fileCounters = new HashMap<>();

        try (PrintWriter meta = new PrintWriter(Files.newBufferedWriter(metaPath, StandardCharsets.UTF_8))) {
            for (SiteMapEntry entry : entries) {
                try {
                    HttpResponse response = entry.item().response();
                    if (response == null) continue;

                    String fileName = buildFilePath(entry, fileCounters, response);
                    Path filePath = assetsPath.resolve(fileName);

                    Files.createDirectories(filePath.getParent());
                    Files.write(filePath, response.body().getBytes());

                    String relPath = assetsPath.getParent().relativize(filePath).toString();
                    meta.println(buildMetaJson(entry, relPath));

                    count++;
                    if (onProgress != null) onProgress.run();
                } catch (Exception e) {
                    logging.logToError("Asset export error: " + e.getMessage());
                }
            }
        }
        return count;
    }

    private String buildFilePath(SiteMapEntry entry, Map<String, Integer> fileCounters, HttpResponse response) {
        java.net.URI uri = java.net.URI.create(entry.url());
        String path = uri.getPath();
        if (path == null || path.isEmpty() || path.equals("/")) path = "/index";

        String[] parts = path.split("/");
        String baseName = parts[parts.length - 1];
        if (baseName.isEmpty()) baseName = "index";

        StringBuilder dirBuilder = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (!parts[i].isEmpty()) {
                if (!dirBuilder.isEmpty()) dirBuilder.append("/");
                dirBuilder.append(FileUtils.sanitize(parts[i]));
            }
        }

        String fileName = FileUtils.sanitize(baseName);
        if (!fileName.contains(".")) {
            String ext = FileUtils.getExtensionForContentType(response);
            if (ext != null) fileName += ext;
        }

        String dirPath = dirBuilder.toString();
        String key = dirPath + "/" + fileName;
        int n = fileCounters.getOrDefault(key, 0);
        fileCounters.put(key, n + 1);

        if (n > 0) {
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) {
                fileName = fileName.substring(0, dot) + "_" + n + fileName.substring(dot);
            } else {
                fileName += "_" + n;
            }
        }

        return dirPath.isEmpty() ? fileName : dirPath + "/" + fileName;
    }

    private String buildMetaJson(SiteMapEntry entry, String assetPath) {
        HttpRequest req = entry.item().request();
        HttpResponse resp = entry.item().response();

        return "{" +
                "\"url\":" + JsonUtils.escape(entry.url()) + "," +
                "\"method\":" + JsonUtils.escape(req.method()) + "," +
                "\"asset_path\":" + JsonUtils.escape(assetPath) + "," +
                "\"status\":" + resp.statusCode() + "," +
                "\"size\":" + resp.body().length() + "," +
                "\"content_type\":" + JsonUtils.escape(entry.contentType()) +
                "}";
    }
}
