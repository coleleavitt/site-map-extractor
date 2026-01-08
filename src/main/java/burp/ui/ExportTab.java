package burp.ui;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.scope.Scope;
import burp.api.montoya.sitemap.SiteMap;
import burp.export.AssetExporter;
import burp.export.HarExporter;
import burp.export.JsonlExporter;
import burp.model.ExportResult;
import burp.model.SiteMapEntry;
import burp.util.FileUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class ExportTab extends JPanel {

    private final Logging logging;
    private final SiteMap siteMap;
    private final Scope scope;

    private JRadioButton scopeOnlyRadio;

    private JCheckBox filterJsCheckbox;
    private JCheckBox filterCssCheckbox;
    private JCheckBox filterHtmlCheckbox;
    private JCheckBox filterJsonCheckbox;
    private JCheckBox filterImagesCheckbox;
    private JCheckBox filterFontsCheckbox;
    private JCheckBox filterOtherCheckbox;

    private JTextField pathIncludeField;
    private JTextField pathExcludeField;

    private JTable previewTable;
    private DefaultTableModel previewTableModel;

    private JCheckBox exportAssetsCheckbox;
    private JCheckBox exportJsonlCheckbox;
    private JCheckBox exportHarCheckbox;
    private JCheckBox dedupeByUrlCheckbox;

    private JProgressBar progressBar;
    private JLabel statusLabel;

    private List<SiteMapEntry> allEntries = new ArrayList<>();

    public ExportTab(Logging logging, SiteMap siteMap, Scope scope) {
        this.logging = logging;
        this.siteMap = siteMap;
        this.scope = scope;
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
        add(createBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        topPanel.add(createScopePanel());
        topPanel.add(createContentTypeFilterPanel());
        topPanel.add(createPathFilterPanel());

        return topPanel;
    }

    private JPanel createScopePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        scopeOnlyRadio = new JRadioButton("In-scope only", true);
        JRadioButton fullSiteMapRadio = new JRadioButton("Full site map", false);
        ButtonGroup scopeGroup = new ButtonGroup();
        scopeGroup.add(scopeOnlyRadio);
        scopeGroup.add(fullSiteMapRadio);

        JButton refreshButton = new JButton("Refresh Preview");
        refreshButton.addActionListener(e -> refreshPreview());

        panel.add(scopeOnlyRadio);
        panel.add(fullSiteMapRadio);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(refreshButton);

        return panel;
    }

    private JPanel createContentTypeFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Content-Type Filters"));

        filterJsCheckbox = new JCheckBox("JS", true);
        filterCssCheckbox = new JCheckBox("CSS", true);
        filterHtmlCheckbox = new JCheckBox("HTML", true);
        filterJsonCheckbox = new JCheckBox("JSON", false);
        filterImagesCheckbox = new JCheckBox("Images", false);
        filterFontsCheckbox = new JCheckBox("Fonts", false);
        filterOtherCheckbox = new JCheckBox("Other", false);

        JCheckBox[] checkboxes = {filterJsCheckbox, filterCssCheckbox, filterHtmlCheckbox,
                filterJsonCheckbox, filterImagesCheckbox, filterFontsCheckbox, filterOtherCheckbox};

        for (JCheckBox cb : checkboxes) {
            panel.add(cb);
            cb.addActionListener(e -> applyFilters());
        }

        JButton selectAllBtn = new JButton("All");
        selectAllBtn.addActionListener(e -> setAllFilters(true));
        JButton selectNoneBtn = new JButton("None");
        selectNoneBtn.addActionListener(e -> setAllFilters(false));
        JButton selectStaticBtn = new JButton("Static Only");
        selectStaticBtn.addActionListener(e -> {
            filterJsCheckbox.setSelected(true);
            filterCssCheckbox.setSelected(true);
            filterHtmlCheckbox.setSelected(true);
            filterJsonCheckbox.setSelected(false);
            filterImagesCheckbox.setSelected(true);
            filterFontsCheckbox.setSelected(true);
            filterOtherCheckbox.setSelected(false);
            applyFilters();
        });

        panel.add(Box.createHorizontalStrut(20));
        panel.add(selectAllBtn);
        panel.add(selectNoneBtn);
        panel.add(selectStaticBtn);

        return panel;
    }

    private JPanel createPathFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Path Filters (glob patterns, comma-separated)"));

        pathIncludeField = new JTextField(25);
        pathIncludeField.setToolTipText("e.g., *.js, /static/*, /assets/*");
        pathExcludeField = new JTextField(25);
        pathExcludeField.setToolTipText("e.g., /api/*, /graphql, *.map");

        DocumentListener filterListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                applyFilters();
            }

            public void removeUpdate(DocumentEvent e) {
                applyFilters();
            }

            public void changedUpdate(DocumentEvent e) {
                applyFilters();
            }
        };
        pathIncludeField.getDocument().addDocumentListener(filterListener);
        pathExcludeField.getDocument().addDocumentListener(filterListener);

        panel.add(new JLabel("Include:"));
        panel.add(pathIncludeField);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(new JLabel("Exclude:"));
        panel.add(pathExcludeField);

        return panel;
    }

    private JScrollPane createTablePanel() {
        String[] columns = {"", "URL", "Status", "Type", "Size", "Path"};
        previewTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int col) {
                return switch (col) {
                    case 0 -> Boolean.class;
                    case 2 -> Integer.class;
                    case 4 -> Long.class;
                    default -> String.class;
                };
            }

            @Override
            public boolean isCellEditable(int row, int col) {
                return col == 0;
            }
        };

        previewTable = new JTable(previewTableModel);
        previewTable.getColumnModel().getColumn(0).setMaxWidth(30);
        previewTable.getColumnModel().getColumn(2).setMaxWidth(60);
        previewTable.getColumnModel().getColumn(3).setMaxWidth(80);
        previewTable.getColumnModel().getColumn(4).setMaxWidth(80);
        previewTable.setRowSorter(new TableRowSorter<>(previewTableModel));

        JScrollPane scrollPane = new JScrollPane(previewTable);
        scrollPane.setPreferredSize(new Dimension(800, 400));
        return scrollPane;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));

        bottomPanel.add(createExportOptionsPanel());
        bottomPanel.add(createActionPanel());

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        bottomPanel.add(progressBar);

        return bottomPanel;
    }

    private JPanel createExportOptionsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Export Options"));

        exportAssetsCheckbox = new JCheckBox("Raw Assets (recommended)", true);
        exportAssetsCheckbox.setToolTipText("Saves response bodies as raw files with original names + metadata.jsonl");
        exportJsonlCheckbox = new JCheckBox("JSONL", false);
        exportJsonlCheckbox.setToolTipText("JSON Lines format - one entry per line with full request/response");
        exportHarCheckbox = new JCheckBox("HAR", false);
        exportHarCheckbox.setToolTipText("HTTP Archive format - importable in browser devtools");
        dedupeByUrlCheckbox = new JCheckBox("Deduplicate by URL", true);
        dedupeByUrlCheckbox.setToolTipText("Skip duplicate URLs (keeps first occurrence)");

        panel.add(exportAssetsCheckbox);
        panel.add(exportJsonlCheckbox);
        panel.add(exportHarCheckbox);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(dedupeByUrlCheckbox);

        return panel;
    }

    private JPanel createActionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton selectAllRowsBtn = new JButton("Select All Visible");
        selectAllRowsBtn.addActionListener(e -> selectAllVisible(true));
        JButton deselectAllBtn = new JButton("Deselect All");
        deselectAllBtn.addActionListener(e -> selectAllVisible(false));

        JButton exportButton = new JButton("Export Selected");
        exportButton.setFont(exportButton.getFont().deriveFont(Font.BOLD));
        exportButton.addActionListener(e -> exportSelected());

        statusLabel = new JLabel("Click 'Refresh Preview' to load site map");

        panel.add(selectAllRowsBtn);
        panel.add(deselectAllBtn);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(exportButton);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(statusLabel);

        return panel;
    }

    private void setAllFilters(boolean selected) {
        filterJsCheckbox.setSelected(selected);
        filterCssCheckbox.setSelected(selected);
        filterHtmlCheckbox.setSelected(selected);
        filterJsonCheckbox.setSelected(selected);
        filterImagesCheckbox.setSelected(selected);
        filterFontsCheckbox.setSelected(selected);
        filterOtherCheckbox.setSelected(selected);
        applyFilters();
    }

    private void selectAllVisible(boolean selected) {
        for (int viewRow = 0; viewRow < previewTable.getRowCount(); viewRow++) {
            int modelRow = previewTable.convertRowIndexToModel(viewRow);
            previewTableModel.setValueAt(selected, modelRow, 0);
        }
    }

    private void refreshPreview() {
        statusLabel.setText("Loading site map...");
        allEntries.clear();
        previewTableModel.setRowCount(0);

        new SwingWorker<List<SiteMapEntry>, Void>() {
            @Override
            protected List<SiteMapEntry> doInBackground() {
                List<SiteMapEntry> entries = new ArrayList<>();
                List<HttpRequestResponse> items = siteMap.requestResponses();
                Set<String> seenUrls = new HashSet<>();

                for (HttpRequestResponse item : items) {
                    HttpRequest request = item.request();
                    HttpResponse response = item.response();

                    if (response == null) continue;

                    String url = request.url();
                    if (scopeOnlyRadio.isSelected() && !scope.isInScope(url)) continue;

                    if (dedupeByUrlCheckbox.isSelected()) {
                        if (seenUrls.contains(url)) continue;
                        seenUrls.add(url);
                    }

                    String contentType = FileUtils.categorizeContentType(response);
                    String path;
                    try {
                        URI uri = URI.create(url);
                        path = uri.getPath() != null ? uri.getPath() : "/";
                    } catch (Exception e) {
                        path = "/";
                    }

                    entries.add(new SiteMapEntry(item, url, response.statusCode(),
                            response.body().length(), contentType, path));
                }
                return entries;
            }

            @Override
            protected void done() {
                try {
                    allEntries = get();
                    applyFilters();
                    statusLabel.setText("Loaded " + allEntries.size() + " entries");
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                    logging.logToError("Error loading site map: " + e.getMessage());
                }
            }
        }.execute();
    }

    private Set<String> getAllowedTypes() {
        Set<String> allowedTypes = new HashSet<>();
        if (filterJsCheckbox.isSelected()) allowedTypes.add("JS");
        if (filterCssCheckbox.isSelected()) allowedTypes.add("CSS");
        if (filterHtmlCheckbox.isSelected()) allowedTypes.add("HTML");
        if (filterJsonCheckbox.isSelected()) allowedTypes.add("JSON");
        if (filterImagesCheckbox.isSelected()) allowedTypes.add("Image");
        if (filterFontsCheckbox.isSelected()) allowedTypes.add("Font");
        if (filterOtherCheckbox.isSelected()) allowedTypes.add("Other");
        return allowedTypes;
    }

    private void applyFilters() {
        previewTableModel.setRowCount(0);

        Set<String> allowedTypes = getAllowedTypes();
        List<Pattern> includePatterns = parseGlobPatterns(pathIncludeField.getText().trim());
        List<Pattern> excludePatterns = parseGlobPatterns(pathExcludeField.getText().trim());

        int count = 0;
        long totalSize = 0;

        for (SiteMapEntry entry : allEntries) {
            if (!allowedTypes.contains(entry.contentType())) continue;

            if (!includePatterns.isEmpty()) {
                boolean matched = includePatterns.stream()
                        .anyMatch(p -> p.matcher(entry.path()).matches() || p.matcher(entry.url()).find());
                if (!matched) continue;
            }

            if (!excludePatterns.isEmpty()) {
                boolean excluded = excludePatterns.stream()
                        .anyMatch(p -> p.matcher(entry.path()).matches() || p.matcher(entry.url()).find());
                if (excluded) continue;
            }

            previewTableModel.addRow(new Object[]{
                    true,
                    entry.url(),
                    entry.status(),
                    entry.contentType(),
                    entry.size(),
                    entry.path()
            });
            count++;
            totalSize += entry.size();
        }

        statusLabel.setText(count + " entries (" + FileUtils.formatSize(totalSize) + ")");
    }

    private List<Pattern> parseGlobPatterns(String text) {
        List<Pattern> patterns = new ArrayList<>();
        if (text.isEmpty()) return patterns;

        for (String glob : text.split(",")) {
            glob = glob.trim();
            if (glob.isEmpty()) continue;

            String regex = glob
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .replace("?", ".");

            try {
                patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                logging.logToError("Invalid pattern: " + glob);
            }
        }
        return patterns;
    }

    private void exportSelected() {
        List<SiteMapEntry> toExport = new ArrayList<>();

        for (int viewRow = 0; viewRow < previewTable.getRowCount(); viewRow++) {
            int modelRow = previewTable.convertRowIndexToModel(viewRow);
            Boolean selected = (Boolean) previewTableModel.getValueAt(modelRow, 0);
            if (selected != null && selected) {
                String url = (String) previewTableModel.getValueAt(modelRow, 1);
                for (SiteMapEntry entry : allEntries) {
                    if (entry.url().equals(url)) {
                        toExport.add(entry);
                        break;
                    }
                }
            }
        }

        if (toExport.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No entries selected for export.");
            return;
        }

        if (!exportAssetsCheckbox.isSelected() && !exportJsonlCheckbox.isSelected() && !exportHarCheckbox.isSelected()) {
            JOptionPane.showMessageDialog(this, "Please select at least one export format.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Export Directory");

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File baseDir = chooser.getSelectedFile();
        Path exportPath = baseDir.toPath().resolve("sitemap_export_" + System.currentTimeMillis());

        boolean doAssets = exportAssetsCheckbox.isSelected();
        boolean doJsonl = exportJsonlCheckbox.isSelected();
        boolean doHar = exportHarCheckbox.isSelected();

        progressBar.setVisible(true);
        progressBar.setValue(0);
        statusLabel.setText("Exporting...");

        new SwingWorker<ExportResult, Integer>() {
            @Override
            protected ExportResult doInBackground() throws Exception {
                Files.createDirectories(exportPath);

                Map<String, List<SiteMapEntry>> byDomain = new HashMap<>();
                for (SiteMapEntry e : toExport) {
                    String domain = "unknown";
                    try {
                        URI uri = URI.create(e.url());
                        if (uri.getHost() != null) domain = uri.getHost();
                    } catch (Exception ignored) {
                    }
                    byDomain.computeIfAbsent(domain, k -> new ArrayList<>()).add(e);
                }

                int total = toExport.size();
                int[] processed = {0};
                int assetCount = 0, jsonlCount = 0, harCount = 0;

                AssetExporter assetExporter = new AssetExporter(logging);
                JsonlExporter jsonlExporter = new JsonlExporter(logging);
                HarExporter harExporter = new HarExporter(logging);

                for (Map.Entry<String, List<SiteMapEntry>> domainEntry : byDomain.entrySet()) {
                    String domain = domainEntry.getKey();
                    List<SiteMapEntry> entries = domainEntry.getValue();
                    String safeDomain = FileUtils.sanitize(domain);

                    Path domainPath = exportPath.resolve(safeDomain);
                    Files.createDirectories(domainPath);

                    if (doAssets) {
                        Path assetsPath = domainPath.resolve("assets");
                        Files.createDirectories(assetsPath);
                        Path metaPath = domainPath.resolve("metadata.jsonl");
                        assetCount += assetExporter.export(entries, assetsPath, metaPath, () -> {
                            processed[0]++;
                            publish((processed[0] * 100) / total);
                        });
                    }

                    if (doJsonl) {
                        Path jsonlPath = domainPath.resolve("sitemap.jsonl");
                        jsonlCount += jsonlExporter.export(entries, jsonlPath);
                    }

                    if (doHar) {
                        Path harPath = domainPath.resolve("sitemap.har");
                        harCount += harExporter.export(entries, harPath);
                    }

                    if (!doAssets) {
                        processed[0] += entries.size();
                        publish((processed[0] * 100) / total);
                    }
                }

                return new ExportResult(exportPath, byDomain.size(), assetCount, jsonlCount, harCount);
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) {
                    progressBar.setValue(chunks.getLast());
                }
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    ExportResult r = get();
                    statusLabel.setText("Export complete: " + r.assetCount() + " assets");

                    String msg = "Export complete!\n\n" +
                            "Location: " + r.path() + "\n" +
                            "Domains: " + r.domains() + "\n\n" +
                            (doAssets ? "Assets: " + r.assetCount() + "\n" : "") +
                            (doJsonl ? "JSONL entries: " + r.jsonlCount() + "\n" : "") +
                            (doHar ? "HAR entries: " + r.harCount() + "\n" : "");

                    JOptionPane.showMessageDialog(ExportTab.this, msg);
                } catch (Exception e) {
                    statusLabel.setText("Export failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(ExportTab.this, "Error: " + e.getMessage(),
                            "Export Error", JOptionPane.ERROR_MESSAGE);
                    logging.logToError("Export error: " + e.getMessage());
                }
            }
        }.execute();
    }
}
