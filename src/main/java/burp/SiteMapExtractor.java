package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
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
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Site Map Extractor - Burp Suite Extension
 * 
 * Extracts links and response codes from Burp's site map.
 * Ported from Jython to Java using the Montoya API.
 * 
 * @author swright573 (original)
 * @author coleleavitt (Montoya API port)
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
        featuresPanel.setMaximumSize(new Dimension(900, 120));
        
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
        
        JLabel label = new JLabel("Export Site Map to File");
        label.setFont(new Font("Tahoma", Font.BOLD, 14));
        panel.add(label, BorderLayout.NORTH);
        
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        mustHaveResponseRadio = new JRadioButton("Must have a response", true);
        allRequestsRadio = new JRadioButton("All", false);
        ButtonGroup responseGroup = new ButtonGroup();
        responseGroup.add(mustHaveResponseRadio);
        responseGroup.add(allRequestsRadio);
        radioPanel.add(mustHaveResponseRadio);
        radioPanel.add(allRequestsRadio);
        panel.add(radioPanel, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton runButton = new JButton("Run");
        runButton.addActionListener(e -> exportSiteMap());
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
     * Export full site map to file
     */
    private void exportSiteMap() {
        clearLog();
        
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text files", "txt"));
        
        int result = chooser.showSaveDialog(mainPanel);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File file = chooser.getSelectedFile();
        String path = file.getAbsolutePath();
        if (!path.toLowerCase().endsWith(".txt")) {
            file = new File(path + ".txt");
        }
        
        if (file.exists()) {
            int confirm = JOptionPane.showConfirmDialog(mainPanel, 
                "File already exists. Overwrite?", "", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            List<HttpRequestResponse> siteMapData = siteMap.requestResponses();
            boolean requireResponse = mustHaveResponseRadio.isSelected();
            
            for (HttpRequestResponse item : siteMapData) {
                HttpRequest request = item.request();
                String urlString = request.url();
                
                if (isScopeOnly() && !scope.isInScope(urlString)) {
                    continue;
                }
                
                HttpResponse response = item.response();
                
                if (response != null) {
                    writer.println("----- REQUEST");
                    writer.println(request.toString());
                    writer.println("----- RESPONSE");
                    writer.println(response.toString());
                } else if (!requireResponse) {
                    writer.println("----- REQUEST");
                    writer.println(request.toString());
                }
            }
            
            JOptionPane.showMessageDialog(mainPanel, "Site map exported successfully.");
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel, "Error writing file: " + e.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
        }
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
