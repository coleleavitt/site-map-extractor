package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.ui.CodesTab;
import burp.ui.ExportTab;
import burp.ui.LinksTab;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;

public class SiteMapExtractor implements BurpExtension {

    private static final String VERSION = "3.0.0";

    @Override
    public void initialize(MontoyaApi api) {
        Logging logging = api.logging();
        
        api.extension().setName("Site Map Extractor");
        logging.logToOutput("Loading Site Map Extractor v" + VERSION + " ...");

        try {
            SwingUtilities.invokeAndWait(() -> {
                JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
                mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

                JTabbedPane tabbedPane = new JTabbedPane();
                tabbedPane.addTab("Export Assets", new ExportTab(logging, api.siteMap(), api.scope()));
                tabbedPane.addTab("Extract Links", new LinksTab());
                tabbedPane.addTab("Response Codes", new CodesTab());

                mainPanel.add(tabbedPane, BorderLayout.CENTER);
                api.userInterface().registerSuiteTab("Site Map Extractor", mainPanel);
            });
            logging.logToOutput("Site Map Extractor loaded successfully!");
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logging.logToError("Failed to initialize: " + e.getMessage());
            logging.logToError(sw.toString());
            throw new RuntimeException("Extension initialization failed", e);
        }
    }
}
