package burp.export;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import burp.model.SiteMapEntry;
import burp.util.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HarExporter {

    private final Logging logging;

    public HarExporter(Logging logging) {
        this.logging = logging;
    }

    public int export(List<SiteMapEntry> entries, Path path) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"log\":{\"version\":\"1.2\",\"creator\":{\"name\":\"Site Map Extractor\",\"version\":\"3.0.0\"},\"entries\":[");

        int count = 0;
        for (SiteMapEntry e : entries) {
            try {
                HttpRequest req = e.item().request();
                HttpResponse resp = e.item().response();
                if (resp == null) continue;

                if (count > 0) sb.append(",");
                sb.append("{");
                sb.append("\"request\":{\"method\":").append(JsonUtils.escape(req.method()));
                sb.append(",\"url\":").append(JsonUtils.escape(e.url())).append("},");
                sb.append("\"response\":{\"status\":").append(resp.statusCode());
                sb.append(",\"content\":{\"size\":").append(resp.body().length());
                sb.append(",\"text\":").append(JsonUtils.escape(resp.bodyToString())).append("}}");
                sb.append("}");
                count++;
            } catch (Exception ex) {
                logging.logToError("HAR error: " + ex.getMessage());
            }
        }

        sb.append("]}}");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        return count;
    }
}
