import javax.swing.*;
import java.awt.*;

public class TestHtml {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JEditorPane pane = new JEditorPane();
            pane.setContentType("text/html");
            pane.setText("<html><body><pre><code>if (n &lt; 2) return false;</code></pre></body></html>");
            System.out.println("Text from JEditorPane: " + pane.getText());
        });
    }
}
