package burp.export;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import burp.model.SiteMapEntry;
import burp.util.JsonUtils;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class JsonlExporter {

    private final Logging logging;

    public JsonlExporter(Logging logging) {
        this.logging = logging;
    }

    public int export(List<SiteMapEntry> entries, Path path) throws Exception {
        int count = 0;
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            for (SiteMapEntry e : entries) {
                try {
                    HttpRequest req = e.item().request();
                    HttpResponse resp = e.item().response();
                    if (resp == null) continue;

                    String json = "{" +
                            "\"url\":" + JsonUtils.escape(e.url()) + "," +
                            "\"method\":" + JsonUtils.escape(req.method()) + "," +
                            "\"status\":" + resp.statusCode() + "," +
                            "\"request_body\":" + JsonUtils.escape(req.bodyToString()) + "," +
                            "\"response_body\":" + JsonUtils.escape(resp.bodyToString()) +
                            "}";

                    w.println(json);
                    count++;
                } catch (Exception ex) {
                    logging.logToError("JSONL error: " + ex.getMessage());
                }
            }
        }
        return count;
    }
}
