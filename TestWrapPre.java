import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.*;

public class TestWrapPre {
    public static void main(String[] args) {
        JEditorPane p = new JEditorPane();
        HTMLEditorKit kit = new HTMLEditorKit() {
            @Override
            public ViewFactory getViewFactory() {
                return new HTMLFactory() {
                    @Override
                    public View create(Element elem) {
                        AttributeSet attrs = elem.getAttributes();
                        Object elementName = attrs.getAttribute(AbstractDocument.ElementNameAttribute);
                        Object o = (elementName != null) ? null : attrs.getAttribute(StyleConstants.NameAttribute);
                        if (o instanceof HTML.Tag) {
                            HTML.Tag kind = (HTML.Tag) o;
                            if (kind == HTML.Tag.IMPLIED) {
                                String ws = (String) elem.getAttributes().getAttribute(CSS.Attribute.WHITE_SPACE);
                                if ((ws != null) && ws.equals("pre")) {
                                    return new WrapLabelView(elem);
                                }
                            }
                        }
                        return super.create(elem);
                    }
                };
            }
        };
        p.setEditorKit(kit);
        p.setText("<html><head><style>pre { white-space: pre-wrap; }</style></head><body><pre>This is a very very very very very very very very very very very long line of code that we hope will wrap around the edge of the container.</pre></body></html>");
        System.out.println("Preferred width: " + p.getPreferredSize().width);
    }
    
    static class WrapLabelView extends LabelView {
        public WrapLabelView(Element elem) { super(elem); }
        @Override
        public float getMinimumSpan(int axis) {
            switch (axis) {
                case View.X_AXIS: return 0;
                case View.Y_AXIS: return super.getMinimumSpan(axis);
                default: throw new IllegalArgumentException("Invalid axis: " + axis);
            }
        }
    }
}
