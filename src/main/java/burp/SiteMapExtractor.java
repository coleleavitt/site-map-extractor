package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.sitemap.SiteMap;
import burp.api.montoya.scope.Scope;
import burp.api.montoya.utilities.URLUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Site Map Extractor - Burp Suite Extension
 * 
 * Extracts links and response codes from Burp's site map.
 * Supports multiple export formats: Directory Tree, JSONL, HAR.
 * 
 * @author swright573 (original)
 * @author coleleavitt (Montoya API port + structured exports)
 */
public class SiteMapExtractor implements BurpExtension {
    
    private MontoyaApi api;
    private Logging logging;
    private SiteMap siteMap;
    private Scope scope;
    private URLUtils urlUtils;
    
    // UI Components
    private JPanel mainPanel;
    private JRadioButton scopeOnlyRadio;
    private JRadioButton fullSiteMapRadio;
    private JCheckBox linksAbsCheckbox;
    private JCheckBox linksRelCheckbox;
    private JCheckBox rcode1xxCheckbox;
    private JCheckBox rcode2xxCheckbox;
    private JCheckBox rcode3xxCheckbox;
    private JCheckBox rcode4xxCheckbox;
    private JCheckBox rcode5xxCheckbox;
    private JRadioButton mustHaveResponseRadio;
    private JRadioButton allRequestsRadio;
    
    // Export format checkboxes
    private JCheckBox exportTreeCheckbox;
    private JCheckBox exportJsonlCheckbox;
    private JCheckBox exportHarCheckbox;
    
    private JScrollPane logPane;
    private JTable logTable;
    
    // Table data
    private List<Object[]> tableData = new ArrayList<>();
    private String[] colNames = {};
    
    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        this.logging = api.logging();
        this.siteMap = api.siteMap();
        this.scope = api.scope();
        this.urlUtils = api.utilities().urlUtils();
        
        api.extension().setName("Site Map Extractor");
        
        logging.logToOutput("Loading Site Map Extractor ...");
        
        // Build UI on EDT
        SwingUtilities.invokeLater(this::buildUI);
        
        logging.logToOutput("\nSite Map Extractor extension loaded successfully!");
    }
    
    private void buildUI() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Title
        JLabel titleLabel = new JLabel("Site Map Extractor Options");
        titleLabel.setFont(new Font("Tahoma", Font.BOLD, 14));
        titleLabel.setForeground(new Color(235, 136, 0));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // Scope options
        JPanel scopePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        scopePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        scopeOnlyRadio = new JRadioButton("In-scope only", true);
        fullSiteMapRadio = new JRadioButton("Full site map", false);
        ButtonGroup scopeGroup = new ButtonGroup();
        scopeGroup.add(scopeOnlyRadio);
        scopeGroup.add(fullSiteMapRadio);
        scopePanel.add(scopeOnlyRadio);
        scopePanel.add(fullSiteMapRadio);
        mainPanel.add(scopePanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // Three feature panels in a horizontal split
        JPanel featuresPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        featuresPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        featuresPanel.setMaximumSize(new Dimension(900, 150));
        
        // Panel 1: Extract Links
        featuresPanel.add(createLinksPanel());
        
        // Panel 2: Extract Response Codes
        featuresPanel.add(createCodesPanel());
        
        // Panel 3: Export Site Map
        featuresPanel.add(createExportPanel());
        
        mainPanel.add(featuresPanel);
        mainPanel.add(Box.createVerticalStrut(20));
        
        // Log label
        JLabel logLabel = new JLabel("Log:");
        logLabel.setFont(new Font("Tahoma", Font.BOLD, 14));
        logLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(logLabel);
        mainPanel.add(Box.createVerticalStrut(5));
        
        // Log table
        logPane = new JScrollPane();
        logPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        logPane.setPreferredSize(new Dimension(800, 400));
        mainPanel.add(logPane);
        
        // Register the tab
        api.userInterface().registerSuiteTab("Site Map Extractor", mainPanel);
    }
    
    private JPanel createLinksPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        JLabel label = new JLabel("Extract '<a href=' Links");
        label.setFont(new Font("Tahoma", Font.BOLD, 14));
        panel.add(label, BorderLayout.NORTH);
        
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        linksAbsCheckbox = new JCheckBox("Absolute", true);
        linksRelCheckbox = new JCheckBox("Relative", true);
        checkboxPanel.add(linksAbsCheckbox);
        checkboxPanel.add(linksRelCheckbox);
        panel.add(checkboxPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton runButton = new JButton("Run");
        runButton.addActionListener(e -> extractLinks());
        JButton saveButton = new JButton("Save Log to CSV");
        saveButton.addActionListener(e -> saveToCSV());
        JButton clearButton = new JButton("Clear Log");
        clearButton.addActionListener(e -> clearLog());
        buttonPanel.add(runButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(clearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createCodesPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        JLabel label = new JLabel("Extract Response Codes");
        label.setFont(new Font("Tahoma", Font.BOLD, 14));
        panel.add(label, BorderLayout.NORTH);
        
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rcode1xxCheckbox = new JCheckBox("1XX", false);
        rcode2xxCheckbox = new JCheckBox("2XX", true);
        rcode3xxCheckbox = new JCheckBox("3XX", true);
        rcode4xxCheckbox = new JCheckBox("4XX", true);
        rcode5xxCheckbox = new JCheckBox("5XX", true);
        checkboxPanel.add(rcode1xxCheckbox);
        checkboxPanel.add(rcode2xxCheckbox);
        checkboxPanel.add(rcode3xxCheckbox);
        checkboxPanel.add(rcode4xxCheckbox);
        checkboxPanel.add(rcode5xxCheckbox);
        panel.add(checkboxPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton runButton = new JButton("Run");
        runButton.addActionListener(e -> exportCodes());
        JButton saveButton = new JButton("Save Log to CSV");
        saveButton.addActionListener(e -> saveToCSV());
        JButton clearButton = new JButton("Clear Log");
        clearButton.addActionListener(e -> clearLog());
        buttonPanel.add(runButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(clearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createExportPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        JLabel label = new JLabel("Export Site Map");
        label.setFont(new Font("Tahoma", Font.BOLD, 14));
        panel.add(label, BorderLayout.NORTH);
        
        // Center panel with options
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        
        // Response filter
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        mustHaveResponseRadio = new JRadioButton("With response", true);
        allRequestsRadio = new JRadioButton("All", false);
        ButtonGroup responseGroup = new ButtonGroup();
        responseGroup.add(mustHaveResponseRadio);
        responseGroup.add(allRequestsRadio);
        radioPanel.add(mustHaveResponseRadio);
        radioPanel.add(allRequestsRadio);
        centerPanel.add(radioPanel);
        
        // Format selection
        JPanel formatPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        formatPanel.add(new JLabel("Format:"));
        exportTreeCheckbox = new JCheckBox("Tree", true);
        exportTreeCheckbox.setToolTipText("Directory structure: domain/path/METHOD_STATUS.json");
        exportJsonlCheckbox = new JCheckBox("JSONL", true);
        exportJsonlCheckbox.setToolTipText("JSON Lines file - one entry per line, grep-friendly");
        exportHarCheckbox = new JCheckBox("HAR", false);
        exportHarCheckbox.setToolTipText("HTTP Archive format - importable in browser devtools");
        formatPanel.add(exportTreeCheckbox);
        formatPanel.add(exportJsonlCheckbox);
        formatPanel.add(exportHarCheckbox);
        centerPanel.add(formatPanel);
        
        panel.add(centerPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton runButton = new JButton("Export");
        runButton.addActionListener(e -> exportSiteMapStructured());
        JButton clearButton = new JButton("Clear Log");
        clearButton.addActionListener(e -> clearLog());
        buttonPanel.add(runButton);
        buttonPanel.add(clearButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private boolean isScopeOnly() {
        return scopeOnlyRadio.isSelected();
    }
    
    private void clearLog() {
        tableData.clear();
        logPane.setViewportView(null);
    }
    
    /**
     * Extract <a href= links from response bodies
     */
    private void extractLinks() {
        clearLog();
        
        List<HttpRequestResponse> siteMapData = siteMap.requestResponses();
        
        boolean extractAbs = linksAbsCheckbox.isSelected();
        boolean extractRel = linksRelCheckbox.isSelected();
        
        colNames = new String[]{"Page", "HTTPS?", "Link", "Description", "Target", "Rel=", "Possible vulnerabilities"};
        tableData = new ArrayList<>();
        
        for (HttpRequestResponse item : siteMapData) {
            try {
                HttpRequest request = item.request();
                String urlString = request.url();
                
                if (isScopeOnly() && !scope.isInScope(urlString)) {
                    continue;
                }
                
                HttpResponse response = item.response();
                if (response == null) {
                    continue;
                }
                
                String responseBody = response.bodyToString();
                String urlDecoded = urlUtils.decode(urlString);
                
                // Parse links from response body
                parseLinksFromBody(urlDecoded, responseBody, extractAbs, extractRel);
                
            } catch (Exception e) {
                logging.logToError("Error processing link: " + e.getMessage());
            }
        }
        
        updateLogTable();
    }
    
    private void parseLinksFromBody(String pageUrl, String body, boolean extractAbs, boolean extractRel) {
        // Pattern to find <a href=...> tags
        Pattern linkPattern = Pattern.compile("<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        
        Matcher matcher = linkPattern.matcher(body);
        
        while (matcher.find()) {
            String href = matcher.group(1).trim();
            String description = matcher.group(2).replaceAll("<[^>]+>", "").trim(); // Strip inner HTML
            
            // Determine link type
            boolean isAbsolute = href.toLowerCase().startsWith("http://") || 
                                 href.toLowerCase().startsWith("https://") ||
                                 href.toLowerCase().startsWith("mailto:");
            boolean isRelative = !isAbsolute;
            
            if ((isAbsolute && !extractAbs) || (isRelative && !extractRel)) {
                continue;
            }
            
            // Determine HTTPS status
            String isHttps;
            String vulnerabilities = "";
            
            if (href.toLowerCase().startsWith("http://")) {
                isHttps = "false";
                vulnerabilities = "Unencrypted transport";
            } else if (href.toLowerCase().startsWith("https://")) {
                isHttps = "true";
            } else if (href.toLowerCase().startsWith("mailto:")) {
                isHttps = "(mailto)";
            } else {
                isHttps = "(Relative)";
            }
            
            // Extract target attribute
            String target = "";
            Pattern targetPattern = Pattern.compile("target\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher targetMatcher = targetPattern.matcher(matcher.group(0));
            if (targetMatcher.find()) {
                target = targetMatcher.group(1);
            }
            
            // Extract rel attribute
            String rel = "";
            Pattern relPattern = Pattern.compile("\\srel\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher relMatcher = relPattern.matcher(matcher.group(0));
            if (relMatcher.find()) {
                rel = relMatcher.group(1);
            }
            
            // Check for tabnabbing vulnerability
            if (!target.isEmpty() && isAbsolute) {
                if (!rel.toLowerCase().contains("noopener")) {
                    vulnerabilities = vulnerabilities.isEmpty() ? "Tabnabbing" : vulnerabilities + " Tabnabbing";
                }
            }
            
            tableData.add(new Object[]{
                stripURLPort(pageUrl),
                isHttps,
                href.replaceAll("[\\r\\n]+", "").trim(),
                description.replaceAll("[\\r\\n]+", "").trim(),
                target,
                rel,
                vulnerabilities
            });
        }
    }
    
    /**
     * Export response codes from site map
     */
    private void exportCodes() {
        clearLog();
        
        List<HttpRequestResponse> siteMapData = siteMap.requestResponses();
        
        // Build list of response code prefixes to include
        List<Character> rcodes = new ArrayList<>();
        if (rcode1xxCheckbox.isSelected()) rcodes.add('1');
        if (rcode2xxCheckbox.isSelected()) rcodes.add('2');
        if (rcode3xxCheckbox.isSelected()) rcodes.add('3');
        if (rcode4xxCheckbox.isSelected()) rcodes.add('4');
        if (rcode5xxCheckbox.isSelected()) rcodes.add('5');
        
        if (rcodes.contains('3')) {
            colNames = new String[]{"Request", "Referer", "Response Code", "Redirects To"};
        } else {
            colNames = new String[]{"Request", "Referer", "Response Code"};
        }
        tableData = new ArrayList<>();
        
        for (HttpRequestResponse item : siteMapData) {
            try {
                HttpRequest request = item.request();
                String urlString = request.url();
                
                if (isScopeOnly() && !scope.isInScope(urlString)) {
                    continue;
                }
                
                String urlDecoded;
                try {
                    urlDecoded = urlUtils.decode(urlString);
                } catch (Exception e) {
                    // UTF-8 decoding issue - skip this URL
                    continue;
                }
                
                HttpResponse response = item.response();
                if (response == null) {
                    continue;
                }
                
                // Get referer
                String referer = "";
                Optional<String> refererHeader = request.headers().stream()
                    .filter(h -> h.name().equalsIgnoreCase("Referer"))
                    .map(HttpHeader::value)
                    .findFirst();
                if (refererHeader.isPresent()) {
                    String fullReferer = refererHeader.get();
                    // Drop querystring
                    referer = fullReferer.split("\\?")[0];
                }
                
                // Get response code
                int responseCode = response.statusCode();
                char firstDigit = String.valueOf(responseCode).charAt(0);
                
                if (!rcodes.contains(firstDigit)) {
                    continue;
                }
                
                if (firstDigit == '1' || firstDigit == '2' || firstDigit == '4' || firstDigit == '5') {
                    tableData.add(new Object[]{
                        stripURLPort(urlDecoded),
                        referer,
                        String.valueOf(responseCode)
                    });
                } else if (firstDigit == '3') {
                    // Look for Location header - only add if found (fix for no-location bug)
                    Optional<String> locationHeader = response.headers().stream()
                        .filter(h -> h.name().equalsIgnoreCase("Location"))
                        .map(HttpHeader::value)
                        .findFirst();
                    
                    if (locationHeader.isPresent()) {
                        tableData.add(new Object[]{
                            stripURLPort(urlDecoded),
                            referer,
                            String.valueOf(responseCode),
                            locationHeader.get()
                        });
                    }
                }
                
            } catch (Exception e) {
                logging.logToError("Error processing response code: " + e.getMessage());
            }
        }
        
        updateLogTable();
    }
    
    /**
     * Export site map in structured formats (Tree, JSONL, HAR) - organized by domain
     */
    private void exportSiteMapStructured() {
        clearLog();
        
        boolean exportTree = exportTreeCheckbox.isSelected();
        boolean exportJsonl = exportJsonlCheckbox.isSelected();
        boolean exportHar = exportHarCheckbox.isSelected();
        
        if (!exportTree && !exportJsonl && !exportHar) {
            JOptionPane.showMessageDialog(mainPanel, "Please select at least one export format.");
            return;
        }
        
        // Select export directory
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Export Directory");
        
        int result = chooser.showSaveDialog(mainPanel);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File baseDir = chooser.getSelectedFile();
        Path exportPath = baseDir.toPath().resolve("sitemap_export_" + System.currentTimeMillis());
        
        try {
            Files.createDirectories(exportPath);
            
            List<HttpRequestResponse> siteMapData = siteMap.requestResponses();
            boolean requireResponse = mustHaveResponseRadio.isSelected();
            
            // Group entries by domain
            Map<String, List<HttpRequestResponse>> entriesByDomain = new HashMap<>();
            
            for (HttpRequestResponse item : siteMapData) {
                HttpRequest request = item.request();
                String urlString = request.url();
                
                if (isScopeOnly() && !scope.isInScope(urlString)) {
                    continue;
                }
                
                if (requireResponse && item.response() == null) {
                    continue;
                }
                
                // Extract domain
                String domain = "unknown_host";
                try {
                    URI uri = URI.create(urlString);
                    if (uri.getHost() != null) {
                        domain = uri.getHost();
                    }
                } catch (Exception e) {
                    // Keep default
                }
                
                entriesByDomain.computeIfAbsent(domain, k -> new ArrayList<>()).add(item);
            }
            
            // Export each domain separately
            colNames = new String[]{"Domain", "Format", "Path", "Entries"};
            tableData = new ArrayList<>();
            
            int totalTree = 0;
            int totalJsonl = 0;
            int totalHar = 0;
            
            for (Map.Entry<String, List<HttpRequestResponse>> entry : entriesByDomain.entrySet()) {
                String domain = entry.getKey();
                List<HttpRequestResponse> domainEntries = entry.getValue();
                String safeDomain = sanitizePathComponent(domain);
                
                Path domainPath = exportPath.resolve(safeDomain);
                Files.createDirectories(domainPath);
                
                // Export Tree for this domain
                if (exportTree) {
                    Path treePath = domainPath.resolve("tree");
                    Files.createDirectories(treePath);
                    int count = exportAsTreeForDomain(domainEntries, treePath);
                    totalTree += count;
                    tableData.add(new Object[]{domain, "Tree", treePath.toString(), count});
                }
                
                // Export JSONL for this domain
                if (exportJsonl) {
                    Path jsonlFile = domainPath.resolve("sitemap.jsonl");
                    int count = exportAsJsonl(domainEntries, jsonlFile);
                    totalJsonl += count;
                    tableData.add(new Object[]{domain, "JSONL", jsonlFile.toString(), count});
                }
                
                // Export HAR for this domain
                if (exportHar) {
                    Path harFile = domainPath.resolve("sitemap.har");
                    int count = exportAsHar(domainEntries, harFile);
                    totalHar += count;
                    tableData.add(new Object[]{domain, "HAR", harFile.toString(), count});
                }
            }
            
            updateLogTable();
            
            StringBuilder summary = new StringBuilder();
            summary.append("Export complete!\n\n");
            summary.append("Location: ").append(exportPath).append("\n");
            summary.append("Domains: ").append(entriesByDomain.size()).append("\n\n");
            if (exportTree) summary.append("Tree files: ").append(totalTree).append("\n");
            if (exportJsonl) summary.append("JSONL entries: ").append(totalJsonl).append("\n");
            if (exportHar) summary.append("HAR entries: ").append(totalHar).append("\n");
            
            JOptionPane.showMessageDialog(mainPanel, summary.toString());
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel, "Error exporting: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
            logging.logToError("Export error: " + e.getMessage());
        }
    }
    
    /**
     * Export as directory tree structure for a single domain (no host folder since already in domain folder)
     */
    private int exportAsTreeForDomain(List<HttpRequestResponse> entries, Path treePath) throws IOException {
        int count = 0;
        Map<String, Integer> fileCounters = new HashMap<>();
        
        for (HttpRequestResponse item : entries) {
            try {
                HttpRequest request = item.request();
                HttpResponse response = item.response();
                
                String urlString = request.url();
                URI uri = URI.create(urlString);
                
                String path = uri.getPath();
                if (path == null || path.isEmpty()) path = "/";
                
                Path dirPath = treePath;
                
                String[] pathParts = path.split("/");
                for (int i = 0; i < pathParts.length - 1; i++) {
                    String part = pathParts[i];
                    if (!part.isEmpty()) {
                        dirPath = dirPath.resolve(sanitizePathComponent(part));
                    }
                }
                
                Files.createDirectories(dirPath);
                
                // Build filename: METHOD_STATUS[_N].json
                String method = request.method();
                int status = response != null ? response.statusCode() : 0;
                String baseName = method + "_" + status;
                
                // Handle duplicates
                String fileKey = dirPath.toString() + "/" + baseName;
                int counter = fileCounters.getOrDefault(fileKey, 0);
                fileCounters.put(fileKey, counter + 1);
                
                String fileName = counter == 0 ? baseName + ".json" : baseName + "_" + counter + ".json";
                Path filePath = dirPath.resolve(fileName);
                
                // Build JSON content
                String json = buildEntryJson(request, response, urlString);
                Files.writeString(filePath, json, StandardCharsets.UTF_8);
                
                count++;
            } catch (Exception e) {
                logging.logToError("Error exporting tree entry: " + e.getMessage());
            }
        }
        
        return count;
    }
    
    /**
     * Export as JSON Lines format (one JSON object per line)
     */
    private int exportAsJsonl(List<HttpRequestResponse> entries, Path jsonlPath) throws IOException {
        int count = 0;
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(jsonlPath, StandardCharsets.UTF_8))) {
            for (HttpRequestResponse item : entries) {
                try {
                    HttpRequest request = item.request();
                    HttpResponse response = item.response();
                    String urlString = request.url();
                    
                    // Build compact JSON (single line)
                    String json = buildEntryJsonCompact(request, response, urlString);
                    writer.println(json);
                    count++;
                } catch (Exception e) {
                    logging.logToError("Error exporting JSONL entry: " + e.getMessage());
                }
            }
        }
        
        return count;
    }
    
    /**
     * Export as HAR (HTTP Archive) format
     */
    private int exportAsHar(List<HttpRequestResponse> entries, Path harPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"log\": {\n");
        sb.append("    \"version\": \"1.2\",\n");
        sb.append("    \"creator\": {\n");
        sb.append("      \"name\": \"Site Map Extractor\",\n");
        sb.append("      \"version\": \"2.0.0\"\n");
        sb.append("    },\n");
        sb.append("    \"entries\": [\n");
        
        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            HttpRequestResponse item = entries.get(i);
            try {
                HttpRequest request = item.request();
                HttpResponse response = item.response();
                
                if (count > 0) sb.append(",\n");
                sb.append(buildHarEntry(request, response));
                count++;
            } catch (Exception e) {
                logging.logToError("Error exporting HAR entry: " + e.getMessage());
            }
        }
        
        sb.append("\n    ]\n");
        sb.append("  }\n");
        sb.append("}\n");
        
        Files.writeString(harPath, sb.toString(), StandardCharsets.UTF_8);
        
        return count;
    }
    
    /**
     * Build JSON for a single request/response entry (pretty printed)
     */
    private String buildEntryJson(HttpRequest request, HttpResponse response, String url) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        
        // Request section
        sb.append("  \"request\": {\n");
        sb.append("    \"method\": ").append(jsonString(request.method())).append(",\n");
        sb.append("    \"url\": ").append(jsonString(url)).append(",\n");
        sb.append("    \"headers\": {\n");
        
        List<HttpHeader> reqHeaders = request.headers();
        for (int i = 0; i < reqHeaders.size(); i++) {
            HttpHeader h = reqHeaders.get(i);
            sb.append("      ").append(jsonString(h.name())).append(": ").append(jsonString(h.value()));
            if (i < reqHeaders.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    },\n");
        
        String reqBody = request.bodyToString();
        sb.append("    \"body\": ").append(jsonString(reqBody)).append("\n");
        sb.append("  }");
        
        // Response section
        if (response != null) {
            sb.append(",\n  \"response\": {\n");
            sb.append("    \"status\": ").append(response.statusCode()).append(",\n");
            sb.append("    \"statusText\": ").append(jsonString(response.reasonPhrase())).append(",\n");
            sb.append("    \"headers\": {\n");
            
            List<HttpHeader> respHeaders = response.headers();
            for (int i = 0; i < respHeaders.size(); i++) {
                HttpHeader h = respHeaders.get(i);
                sb.append("      ").append(jsonString(h.name())).append(": ").append(jsonString(h.value()));
                if (i < respHeaders.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("    },\n");
            
            String respBody = response.bodyToString();
            sb.append("    \"body\": ").append(jsonString(respBody)).append("\n");
            sb.append("  }");
        }
        
        sb.append("\n}\n");
        return sb.toString();
    }
    
    /**
     * Build compact JSON for JSONL format (single line)
     */
    private String buildEntryJsonCompact(HttpRequest request, HttpResponse response, String url) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        sb.append("\"method\":").append(jsonString(request.method())).append(",");
        sb.append("\"url\":").append(jsonString(url)).append(",");
        
        // Request headers as object
        sb.append("\"request_headers\":{");
        List<HttpHeader> reqHeaders = request.headers();
        for (int i = 0; i < reqHeaders.size(); i++) {
            HttpHeader h = reqHeaders.get(i);
            if (i > 0) sb.append(",");
            sb.append(jsonString(h.name())).append(":").append(jsonString(h.value()));
        }
        sb.append("},");
        
        String reqBody = request.bodyToString();
        sb.append("\"request_body\":").append(jsonString(reqBody));
        
        if (response != null) {
            sb.append(",\"status\":").append(response.statusCode());
            sb.append(",\"status_text\":").append(jsonString(response.reasonPhrase()));
            
            sb.append(",\"response_headers\":{");
            List<HttpHeader> respHeaders = response.headers();
            for (int i = 0; i < respHeaders.size(); i++) {
                HttpHeader h = respHeaders.get(i);
                if (i > 0) sb.append(",");
                sb.append(jsonString(h.name())).append(":").append(jsonString(h.value()));
            }
            sb.append("}");
            
            String respBody = response.bodyToString();
            sb.append(",\"response_body\":").append(jsonString(respBody));
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Build HAR entry for a single request/response
     */
    private String buildHarEntry(HttpRequest request, HttpResponse response) {
        StringBuilder sb = new StringBuilder();
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        
        sb.append("      {\n");
        sb.append("        \"startedDateTime\": \"").append(timestamp).append("\",\n");
        sb.append("        \"time\": 0,\n");
        
        // Request
        sb.append("        \"request\": {\n");
        sb.append("          \"method\": ").append(jsonString(request.method())).append(",\n");
        sb.append("          \"url\": ").append(jsonString(request.url())).append(",\n");
        sb.append("          \"httpVersion\": \"HTTP/1.1\",\n");
        
        // Request headers
        sb.append("          \"headers\": [\n");
        List<HttpHeader> reqHeaders = request.headers();
        for (int i = 0; i < reqHeaders.size(); i++) {
            HttpHeader h = reqHeaders.get(i);
            sb.append("            {\"name\": ").append(jsonString(h.name()))
              .append(", \"value\": ").append(jsonString(h.value())).append("}");
            if (i < reqHeaders.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("          ],\n");
        
        sb.append("          \"queryString\": [],\n");
        sb.append("          \"headersSize\": -1,\n");
        sb.append("          \"bodySize\": ").append(request.body().length()).append(",\n");
        
        // Post data
        String reqBody = request.bodyToString();
        if (!reqBody.isEmpty()) {
            sb.append("          \"postData\": {\n");
            sb.append("            \"mimeType\": \"application/octet-stream\",\n");
            sb.append("            \"text\": ").append(jsonString(reqBody)).append("\n");
            sb.append("          }\n");
        } else {
            sb.append("          \"cookies\": []\n");
        }
        sb.append("        },\n");
        
        // Response
        sb.append("        \"response\": {\n");
        if (response != null) {
            sb.append("          \"status\": ").append(response.statusCode()).append(",\n");
            sb.append("          \"statusText\": ").append(jsonString(response.reasonPhrase())).append(",\n");
            sb.append("          \"httpVersion\": \"HTTP/1.1\",\n");
            
            // Response headers
            sb.append("          \"headers\": [\n");
            List<HttpHeader> respHeaders = response.headers();
            for (int i = 0; i < respHeaders.size(); i++) {
                HttpHeader h = respHeaders.get(i);
                sb.append("            {\"name\": ").append(jsonString(h.name()))
                  .append(", \"value\": ").append(jsonString(h.value())).append("}");
                if (i < respHeaders.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("          ],\n");
            
            sb.append("          \"cookies\": [],\n");
            
            // Content
            String respBody = response.bodyToString();
            String mimeType = response.headers().stream()
                .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
                .map(HttpHeader::value)
                .findFirst()
                .orElse("application/octet-stream");
            
            sb.append("          \"content\": {\n");
            sb.append("            \"size\": ").append(response.body().length()).append(",\n");
            sb.append("            \"mimeType\": ").append(jsonString(mimeType)).append(",\n");
            sb.append("            \"text\": ").append(jsonString(respBody)).append("\n");
            sb.append("          },\n");
            
            sb.append("          \"redirectURL\": \"\",\n");
            sb.append("          \"headersSize\": -1,\n");
            sb.append("          \"bodySize\": ").append(response.body().length()).append("\n");
        } else {
            sb.append("          \"status\": 0,\n");
            sb.append("          \"statusText\": \"\",\n");
            sb.append("          \"httpVersion\": \"HTTP/1.1\",\n");
            sb.append("          \"headers\": [],\n");
            sb.append("          \"cookies\": [],\n");
            sb.append("          \"content\": {\"size\": 0, \"mimeType\": \"\"},\n");
            sb.append("          \"redirectURL\": \"\",\n");
            sb.append("          \"headersSize\": -1,\n");
            sb.append("          \"bodySize\": 0\n");
        }
        sb.append("        },\n");
        
        sb.append("        \"cache\": {},\n");
        sb.append("        \"timings\": {\"send\": 0, \"wait\": 0, \"receive\": 0}\n");
        sb.append("      }");
        
        return sb.toString();
    }
    
    /**
     * Escape and quote a string for JSON
     */
    private String jsonString(String s) {
        if (s == null) return "null";
        
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
    
    /**
     * Sanitize a path component for filesystem safety
     */
    private String sanitizePathComponent(String s) {
        if (s == null || s.isEmpty()) return "_";
        
        // Replace problematic characters
        String sanitized = s.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", "_");
        
        // Limit length
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        
        // Handle reserved names on Windows
        String upper = sanitized.toUpperCase();
        if (upper.matches("^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$")) {
            sanitized = "_" + sanitized;
        }
        
        return sanitized.isEmpty() ? "_" : sanitized;
    }
    
    /**
     * Save log table to CSV file
     */
    private void saveToCSV() {
        if (tableData.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "The log contains no data.");
            return;
        }
        
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        
        int result = chooser.showSaveDialog(mainPanel);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File file = chooser.getSelectedFile();
        String path = file.getAbsolutePath();
        if (!path.toLowerCase().endsWith(".csv")) {
            file = new File(path + ".csv");
        }
        
        if (file.exists()) {
            int confirm = JOptionPane.showConfirmDialog(mainPanel, 
                "File already exists. Overwrite?", "", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Write header
            writer.println(String.join(",", colNames));
            
            // Write data
            for (Object[] row : tableData) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) sb.append(",");
                    String value = row[i] != null ? row[i].toString() : "";
                    // Escape CSV values
                    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                        value = "\"" + value.replace("\"", "\"\"") + "\"";
                    }
                    sb.append(value);
                }
                writer.println(sb);
            }
            
            JOptionPane.showMessageDialog(mainPanel, "CSV file saved successfully.");
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel, "Error writing file: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void updateLogTable() {
        Object[][] data = tableData.toArray(new Object[0][]);
        DefaultTableModel model = new DefaultTableModel(data, colNames);
        logTable = new JTable(model);
        logTable.setAutoCreateRowSorter(true);
        logPane.setViewportView(logTable);
    }
    
    /**
     * Strip port from URL for cleaner display
     * e.g., https://example.com:443/path -> https://example.com/path
     */
    private String stripURLPort(String url) {
        try {
            String[] parts = url.split(":");
            if (parts.length >= 3) {
                String protocol = parts[0];
                String host = parts[1];
                String pathWithPort = parts[2];
                String path = pathWithPort.contains("/") ? 
                    "/" + pathWithPort.split("/", 2)[1] : "";
                return protocol + ":" + host + path;
            }
        } catch (Exception e) {
            // Return original on error
        }
        return url;
    }
}
