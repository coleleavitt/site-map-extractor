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
import java.util.*;
import java.util.List;

public class CodesTab extends JPanel {

    private final Logging logging;
    private final SiteMap siteMap;
    private final Scope scope;

    private JRadioButton scopeOnlyRadio;
    private JCheckBox show1xxCheckbox;
    private JCheckBox show2xxCheckbox;
    private JCheckBox show3xxCheckbox;
    private JCheckBox show4xxCheckbox;
    private JCheckBox show5xxCheckbox;

    private JTable codesTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    private JLabel count1xxLabel;
    private JLabel count2xxLabel;
    private JLabel count3xxLabel;
    private JLabel count4xxLabel;
    private JLabel count5xxLabel;

    private List<ResponseEntry> allResponses = new ArrayList<>();

    public CodesTab(Logging logging, SiteMap siteMap, Scope scope) {
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
        topPanel.add(createSummaryPanel());
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

        JButton analyzeButton = new JButton("Analyze Responses");
        analyzeButton.addActionListener(e -> analyzeResponses());

        panel.add(scopeOnlyRadio);
        panel.add(fullSiteMapRadio);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(analyzeButton);

        return panel;
    }

    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        panel.setBorder(new TitledBorder("Response Code Summary"));

        count1xxLabel = createCountLabel("1xx Info", Color.GRAY);
        count2xxLabel = createCountLabel("2xx Success", new Color(0, 128, 0));
        count3xxLabel = createCountLabel("3xx Redirect", new Color(0, 0, 200));
        count4xxLabel = createCountLabel("4xx Client Error", new Color(200, 100, 0));
        count5xxLabel = createCountLabel("5xx Server Error", Color.RED);

        panel.add(count1xxLabel);
        panel.add(count2xxLabel);
        panel.add(count3xxLabel);
        panel.add(count4xxLabel);
        panel.add(count5xxLabel);

        return panel;
    }

    private JLabel createCountLabel(String text, Color color) {
        JLabel label = new JLabel(text + ": 0");
        label.setForeground(color);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Status Code Filters"));

        show1xxCheckbox = new JCheckBox("1xx", true);
        show2xxCheckbox = new JCheckBox("2xx", true);
        show3xxCheckbox = new JCheckBox("3xx", true);
        show4xxCheckbox = new JCheckBox("4xx", true);
        show5xxCheckbox = new JCheckBox("5xx", true);

        show1xxCheckbox.addActionListener(e -> applyFilters());
        show2xxCheckbox.addActionListener(e -> applyFilters());
        show3xxCheckbox.addActionListener(e -> applyFilters());
        show4xxCheckbox.addActionListener(e -> applyFilters());
        show5xxCheckbox.addActionListener(e -> applyFilters());

        JButton showAllBtn = new JButton("All");
        showAllBtn.addActionListener(e -> setAllFilters(true));

        JButton showErrorsBtn = new JButton("Errors Only");
        showErrorsBtn.addActionListener(e -> {
            show1xxCheckbox.setSelected(false);
            show2xxCheckbox.setSelected(false);
            show3xxCheckbox.setSelected(false);
            show4xxCheckbox.setSelected(true);
            show5xxCheckbox.setSelected(true);
            applyFilters();
        });

        JButton showRedirectsBtn = new JButton("Redirects Only");
        showRedirectsBtn.addActionListener(e -> {
            show1xxCheckbox.setSelected(false);
            show2xxCheckbox.setSelected(false);
            show3xxCheckbox.setSelected(true);
            show4xxCheckbox.setSelected(false);
            show5xxCheckbox.setSelected(false);
            applyFilters();
        });

        panel.add(show1xxCheckbox);
        panel.add(show2xxCheckbox);
        panel.add(show3xxCheckbox);
        panel.add(show4xxCheckbox);
        panel.add(show5xxCheckbox);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(showAllBtn);
        panel.add(showErrorsBtn);
        panel.add(showRedirectsBtn);

        return panel;
    }

    private void setAllFilters(boolean selected) {
        show1xxCheckbox.setSelected(selected);
        show2xxCheckbox.setSelected(selected);
        show3xxCheckbox.setSelected(selected);
        show4xxCheckbox.setSelected(selected);
        show5xxCheckbox.setSelected(selected);
        applyFilters();
    }

    private JScrollPane createTablePanel() {
        String[] columns = {"URL", "Status", "Reason", "Referer", "Location"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int col) {
                return col == 1 ? Integer.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        codesTable = new JTable(tableModel);
        codesTable.getColumnModel().getColumn(0).setPreferredWidth(350);
        codesTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        codesTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        codesTable.getColumnModel().getColumn(3).setPreferredWidth(200);
        codesTable.getColumnModel().getColumn(4).setPreferredWidth(200);
        codesTable.setRowSorter(new TableRowSorter<>(tableModel));

        JScrollPane scrollPane = new JScrollPane(codesTable);
        scrollPane.setPreferredSize(new Dimension(800, 400));
        return scrollPane;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton exportCsvBtn = new JButton("Export to CSV");
        exportCsvBtn.addActionListener(e -> exportToCsv());

        JButton copyUrlsBtn = new JButton("Copy URLs");
        copyUrlsBtn.addActionListener(e -> copyUrls());

        statusLabel = new JLabel("Click 'Analyze Responses' to scan site map");

        actionPanel.add(exportCsvBtn);
        actionPanel.add(copyUrlsBtn);
        actionPanel.add(Box.createHorizontalStrut(20));
        actionPanel.add(statusLabel);

        bottomPanel.add(actionPanel);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        bottomPanel.add(progressBar);

        return bottomPanel;
    }

    private void analyzeResponses() {
        statusLabel.setText("Analyzing responses...");
        allResponses.clear();
        tableModel.setRowCount(0);
        progressBar.setVisible(true);
        progressBar.setValue(0);

        new SwingWorker<List<ResponseEntry>, Integer>() {
            @Override
            protected List<ResponseEntry> doInBackground() {
                List<ResponseEntry> responses = new ArrayList<>();
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

                    int status = response.statusCode();
                    String reason = response.reasonPhrase() != null ? response.reasonPhrase() : "";

                    String referer = request.hasHeader("Referer")
                            ? request.headerValue("Referer")
                            : "";

                    String location = "";
                    if (status >= 300 && status < 400 && response.hasHeader("Location")) {
                        location = response.headerValue("Location");
                    }

                    responses.add(new ResponseEntry(url, status, reason, referer, location));

                    processed++;
                    publish((processed * 100) / total);
                }
                return responses;
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
                    allResponses = get();
                    updateSummaryCounts();
                    applyFilters();
                    statusLabel.setText("Analyzed " + allResponses.size() + " responses");
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                    logging.logToError("Error analyzing responses: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void updateSummaryCounts() {
        int c1xx = 0, c2xx = 0, c3xx = 0, c4xx = 0, c5xx = 0;

        for (ResponseEntry r : allResponses) {
            int cat = r.status / 100;
            switch (cat) {
                case 1 -> c1xx++;
                case 2 -> c2xx++;
                case 3 -> c3xx++;
                case 4 -> c4xx++;
                case 5 -> c5xx++;
            }
        }

        count1xxLabel.setText("1xx Info: " + c1xx);
        count2xxLabel.setText("2xx Success: " + c2xx);
        count3xxLabel.setText("3xx Redirect: " + c3xx);
        count4xxLabel.setText("4xx Client Error: " + c4xx);
        count5xxLabel.setText("5xx Server Error: " + c5xx);
    }

    private void applyFilters() {
        tableModel.setRowCount(0);

        Set<Integer> allowedCategories = new HashSet<>();
        if (show1xxCheckbox.isSelected()) allowedCategories.add(1);
        if (show2xxCheckbox.isSelected()) allowedCategories.add(2);
        if (show3xxCheckbox.isSelected()) allowedCategories.add(3);
        if (show4xxCheckbox.isSelected()) allowedCategories.add(4);
        if (show5xxCheckbox.isSelected()) allowedCategories.add(5);

        int count = 0;
        for (ResponseEntry r : allResponses) {
            int cat = r.status / 100;
            if (!allowedCategories.contains(cat)) continue;

            tableModel.addRow(new Object[]{
                    r.url,
                    r.status,
                    r.reason,
                    truncate(r.referer, 80),
                    truncate(r.location, 80)
            });
            count++;
        }

        statusLabel.setText("Showing " + count + " of " + allResponses.size() + " responses");
    }

    private void exportToCsv() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No responses to export.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("responses_export.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("URL,Status,Reason,Referer,Location");

            for (int i = 0; i < tableModel.getRowCount(); i++) {
                writer.printf("\"%s\",%s,\"%s\",\"%s\",\"%s\"%n",
                        escapeCsv(tableModel.getValueAt(i, 0).toString()),
                        tableModel.getValueAt(i, 1),
                        escapeCsv(tableModel.getValueAt(i, 2).toString()),
                        escapeCsv(tableModel.getValueAt(i, 3).toString()),
                        escapeCsv(tableModel.getValueAt(i, 4).toString())
                );
            }

            statusLabel.setText("Exported " + tableModel.getRowCount() + " responses to " + file.getName());
            JOptionPane.showMessageDialog(this, "Exported to: " + file.getAbsolutePath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            logging.logToError("CSV export error: " + e.getMessage());
        }
    }

    private void copyUrls() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No URLs to copy.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String url = tableModel.getValueAt(i, 0).toString();
            if (seen.add(url)) {
                sb.append(url).append("\n");
            }
        }

        java.awt.datatransfer.StringSelection selection =
                new java.awt.datatransfer.StringSelection(sb.toString());
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

        statusLabel.setText("Copied " + seen.size() + " unique URLs to clipboard");
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.isEmpty()) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    private String escapeCsv(String value) {
        return value.replace("\"", "\"\"");
    }

    private static class ResponseEntry {
        final String url;
        final int status;
        final String reason;
        final String referer;
        final String location;

        ResponseEntry(String url, int status, String reason, String referer, String location) {
            this.url = url;
            this.status = status;
            this.reason = reason;
            this.referer = referer;
            this.location = location;
        }
    }
}
