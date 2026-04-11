import javax.swing.JEditorPane;
public class TestPreWrap {
    public static void main(String[] args) {
        JEditorPane p = new JEditorPane("text/html", "<html><head><style>pre { white-space: pre-wrap; }</style></head><body><pre>This is a very very very very very very very very very very very long line of code that we hope will wrap around the edge of the container.</pre></body></html>");
        System.out.println("Preferred size: " + p.getPreferredSize());
        p.setSize(100, 100);
        System.out.println("Size after forcing 100x100: " + p.getPreferredSize());
    }
}
