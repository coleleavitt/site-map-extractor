package burp.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LinksTab extends JPanel {

    public LinksTab() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        add(new JLabel("Link extraction - coming soon in v3.1"), BorderLayout.CENTER);
    }
}
