package burp.model;

import java.nio.file.Path;

public record ExportResult(
        Path path,
        int domains,
        int assetCount,
        int jsonlCount,
        int harCount
) {}
