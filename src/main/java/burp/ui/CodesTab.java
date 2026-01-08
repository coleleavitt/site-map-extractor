package burp.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CodesTab extends JPanel {

    public CodesTab() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        add(new JLabel("Response codes - coming soon in v3.1"), BorderLayout.CENTER);
    }
}
