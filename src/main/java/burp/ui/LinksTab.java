package burp.ui;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.scope.Scope;
import burp.api.montoya.sitemap.SiteMap;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinksTab extends JPanel {

    private final Logging logging;
    private final SiteMap siteMap;
    private final Scope scope;

    private JRadioButton scopeOnlyRadio;
    private JCheckBox showHttpOnlyCheckbox;
    private JCheckBox showTabnabbingOnlyCheckbox;
    private JCheckBox showExternalOnlyCheckbox;

    private JTable linksTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    private List<LinkEntry> allLinks = new ArrayList<>();

    // Regex: <a ...href="url"...>text</a> - groups: (1)before-href attrs, (2)url, (3)after-href attrs, (4)link text
    private static final Pattern ANCHOR_PATTERN = Pattern.compile(
            "<a\\s+([^>]*?)href\\s*=\\s*[\"']([^\"']+)[\"']([^>]*?)>([^<]*)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TARGET_BLANK_PATTERN = Pattern.compile(
            "target\\s*=\\s*[\"']_blank[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REL_NOOPENER_PATTERN = Pattern.compile(
            "rel\\s*=\\s*[\"'][^\"']*(?:noopener|noreferrer)[^\"']*[\"']",
            Pattern.CASE_INSENSITIVE
    );

    public LinksTab(Logging logging, SiteMap siteMap, Scope scope) {
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
        topPanel.add(createFilterPanel());

        return topPanel;
    }

    private JPanel createScopePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        scopeOnlyRadio = new JRadioButton("In-scope only", true);
        JRadioButton fullSiteMapRadio = new JRadioButton("Full site map", false);
        ButtonGroup scopeGroup = new ButtonGroup();
        scopeGroup.add(scopeOnlyRadio);
        scopeGroup.add(fullSiteMapRadio);

        JButton extractButton = new JButton("Extract Links");
        extractButton.addActionListener(e -> extractLinks());

        panel.add(scopeOnlyRadio);
        panel.add(fullSiteMapRadio);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(extractButton);

        return panel;
    }

    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Issue Filters"));

        showHttpOnlyCheckbox = new JCheckBox("HTTP (unencrypted)", false);
        showTabnabbingOnlyCheckbox = new JCheckBox("Tabnabbing risk", false);
        showExternalOnlyCheckbox = new JCheckBox("External links", false);

        showHttpOnlyCheckbox.addActionListener(e -> applyFilters());
        showTabnabbingOnlyCheckbox.addActionListener(e -> applyFilters());
        showExternalOnlyCheckbox.addActionListener(e -> applyFilters());

        JButton showAllBtn = new JButton("Show All");
        showAllBtn.addActionListener(e -> {
            showHttpOnlyCheckbox.setSelected(false);
            showTabnabbingOnlyCheckbox.setSelected(false);
            showExternalOnlyCheckbox.setSelected(false);
            applyFilters();
        });

        JButton showIssuesBtn = new JButton("Issues Only");
        showIssuesBtn.addActionListener(e -> {
            showHttpOnlyCheckbox.setSelected(true);
            showTabnabbingOnlyCheckbox.setSelected(true);
            showExternalOnlyCheckbox.setSelected(false);
            applyFilters();
        });

        panel.add(showHttpOnlyCheckbox);
        panel.add(showTabnabbingOnlyCheckbox);
        panel.add(showExternalOnlyCheckbox);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(showAllBtn);
        panel.add(showIssuesBtn);

        return panel;
    }

    private JScrollPane createTablePanel() {
        String[] columns = {"Source URL", "Link URL", "Link Text", "Issues"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        linksTable = new JTable(tableModel);
        linksTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        linksTable.getColumnModel().getColumn(1).setPreferredWidth(300);
        linksTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        linksTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        linksTable.setRowSorter(new TableRowSorter<>(tableModel));

        JScrollPane scrollPane = new JScrollPane(linksTable);
        scrollPane.setPreferredSize(new Dimension(800, 400));
        return scrollPane;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton exportCsvBtn = new JButton("Export to CSV");
        exportCsvBtn.addActionListener(e -> exportToCsv());

        JButton copyLinksBtn = new JButton("Copy Link URLs");
        copyLinksBtn.addActionListener(e -> copyLinkUrls());

        statusLabel = new JLabel("Click 'Extract Links' to analyze site map");

        actionPanel.add(exportCsvBtn);
        actionPanel.add(copyLinksBtn);
        actionPanel.add(Box.createHorizontalStrut(20));
        actionPanel.add(statusLabel);

        bottomPanel.add(actionPanel);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        bottomPanel.add(progressBar);

        return bottomPanel;
    }

    private void extractLinks() {
        statusLabel.setText("Extracting links...");
        allLinks.clear();
        tableModel.setRowCount(0);
        progressBar.setVisible(true);
        progressBar.setValue(0);

        new SwingWorker<List<LinkEntry>, Integer>() {
            @Override
            protected List<LinkEntry> doInBackground() {
                List<LinkEntry> links = new ArrayList<>();
                List<HttpRequestResponse> items = siteMap.requestResponses();
                int total = items.size();
                int processed = 0;

                for (HttpRequestResponse item : items) {
                    HttpRequest request = item.request();
                    HttpResponse response = item.response();

                    if (response == null) {
                        processed++;
                        continue;
                    }

                    String url = request.url();
                    if (scopeOnlyRadio.isSelected() && !scope.isInScope(url)) {
                        processed++;
                        continue;
                    }

                    String contentType = response.statedMimeType() != null
                            ? response.statedMimeType().toString().toLowerCase()
                            : "";
                    if (!contentType.contains("html") && !contentType.contains("text")) {
                        processed++;
                        continue;
                    }

                    String body = response.bodyToString();
                    String sourceHost = getHost(url);

                    Matcher matcher = ANCHOR_PATTERN.matcher(body);
                    while (matcher.find()) {
                        String beforeHref = matcher.group(1);
                        String linkUrl = matcher.group(2);
                        String afterHref = matcher.group(3);
                        String linkText = matcher.group(4).trim();
                        String fullTag = beforeHref + afterHref;

                        String absoluteUrl = resolveUrl(url, linkUrl);
                        if (absoluteUrl == null) continue;

                        List<String> issues = new ArrayList<>();

                        if (absoluteUrl.toLowerCase().startsWith("http://")) {
                            issues.add("HTTP");
                        }

                        if (TARGET_BLANK_PATTERN.matcher(fullTag).find()) {
                            if (!REL_NOOPENER_PATTERN.matcher(fullTag).find()) {
                                issues.add("Tabnabbing");
                            }
                        }

                        String linkHost = getHost(absoluteUrl);
                        boolean isExternal = linkHost != null && sourceHost != null
                                && !linkHost.equalsIgnoreCase(sourceHost);
                        if (isExternal) {
                            issues.add("External");
                        }

                        String issueStr = issues.isEmpty() ? "" : String.join(", ", issues);
                        links.add(new LinkEntry(url, absoluteUrl, linkText, issueStr,
                                issues.contains("HTTP"), issues.contains("Tabnabbing"), isExternal));
                    }

                    processed++;
                    publish((processed * 100) / total);
                }
                return links;
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
                    allLinks = get();
                    applyFilters();

                    long httpCount = allLinks.stream().filter(l -> l.isHttp).count();
                    long tabnabCount = allLinks.stream().filter(l -> l.isTabnabbing).count();
                    long extCount = allLinks.stream().filter(l -> l.isExternal).count();

                    statusLabel.setText(String.format(
                            "Found %d links | HTTP: %d | Tabnabbing: %d | External: %d",
                            allLinks.size(), httpCount, tabnabCount, extCount
                    ));
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                    logging.logToError("Error extracting links: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void applyFilters() {
        tableModel.setRowCount(0);

        boolean filterHttp = showHttpOnlyCheckbox.isSelected();
        boolean filterTabnab = showTabnabbingOnlyCheckbox.isSelected();
        boolean filterExternal = showExternalOnlyCheckbox.isSelected();
        boolean anyFilter = filterHttp || filterTabnab || filterExternal;

        int count = 0;
        for (LinkEntry link : allLinks) {
            if (anyFilter) {
                boolean matches = false;
                if (filterHttp && link.isHttp) matches = true;
                if (filterTabnab && link.isTabnabbing) matches = true;
                if (filterExternal && link.isExternal) matches = true;
                if (!matches) continue;
            }

            tableModel.addRow(new Object[]{
                    link.sourceUrl,
                    link.linkUrl,
                    truncate(link.linkText, 50),
                    link.issues
            });
            count++;
        }

        if (anyFilter) {
            statusLabel.setText("Showing " + count + " of " + allLinks.size() + " links (filtered)");
        }
    }

    private void exportToCsv() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No links to export.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("links_export.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("Source URL,Link URL,Link Text,Issues");

            for (int i = 0; i < tableModel.getRowCount(); i++) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        escapeCsv(tableModel.getValueAt(i, 0).toString()),
                        escapeCsv(tableModel.getValueAt(i, 1).toString()),
                        escapeCsv(tableModel.getValueAt(i, 2).toString()),
                        escapeCsv(tableModel.getValueAt(i, 3).toString())
                );
            }

            statusLabel.setText("Exported " + tableModel.getRowCount() + " links to " + file.getName());
            JOptionPane.showMessageDialog(this, "Exported to: " + file.getAbsolutePath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            logging.logToError("CSV export error: " + e.getMessage());
        }
    }

    private void copyLinkUrls() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No links to copy.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String url = tableModel.getValueAt(i, 1).toString();
            if (seen.add(url)) {
                sb.append(url).append("\n");
            }
        }

        java.awt.datatransfer.StringSelection selection =
                new java.awt.datatransfer.StringSelection(sb.toString());
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

        statusLabel.setText("Copied " + seen.size() + " unique URLs to clipboard");
    }

    private String resolveUrl(String base, String relative) {
        try {
            if (relative.startsWith("javascript:") || relative.startsWith("mailto:")
                    || relative.startsWith("tel:") || relative.startsWith("#")) {
                return null;
            }
            URI baseUri = URI.create(base);
            URI resolved = baseUri.resolve(relative);
            return resolved.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String getHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    private String escapeCsv(String value) {
        return value.replace("\"", "\"\"");
    }

    private static class LinkEntry {
        final String sourceUrl;
        final String linkUrl;
        final String linkText;
        final String issues;
        final boolean isHttp;
        final boolean isTabnabbing;
        final boolean isExternal;

        LinkEntry(String sourceUrl, String linkUrl, String linkText, String issues,
                  boolean isHttp, boolean isTabnabbing, boolean isExternal) {
            this.sourceUrl = sourceUrl;
            this.linkUrl = linkUrl;
            this.linkText = linkText;
            this.issues = issues;
            this.isHttp = isHttp;
            this.isTabnabbing = isTabnabbing;
            this.isExternal = isExternal;
        }
    }
}
