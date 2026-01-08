package burp.model;

import burp.api.montoya.http.message.HttpRequestResponse;

public record SiteMapEntry(
        HttpRequestResponse item,
        String url,
        int status,
        long size,
        String contentType,
        String path
) {
}
