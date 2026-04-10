import javax.swing.*;

public class TestHtmlWrappedCode {
    public static void main(String[] args) {
        String code = "public static boolean isPrime(int n) {\n    if (n < 2) return false;\n    for(int i=2; i*i<=n; i++) {\n        if(n%i==0) return false;\n    }\n    return true;\n}";
        
        // Convert to HTML that forces wrapping but keeps visual formatting
        String escaped = code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        // Replace spaces with non-breaking spaces so indenting doesn't collapse
        escaped = escaped.replace("  ", "&nbsp; "); // Simple way to preserve spaces but allow some wrap
        // Replace newlines with <br>
        escaped = escaped.replace("\n", "<br>");
        
        String html = "<html><head><style>div.code { font-family: monospace; background-color: #e9e9d0; padding: 10px; }</style></head>" +
                      "<body><p>Normal text wraps normally. Let's see code:</p>" +
                      "<div class='code'>" + escaped + "</div></body></html>";
                      
        JEditorPane p = new JEditorPane("text/html", html);
        System.out.println("Preferred width: " + p.getPreferredSize().width);
        p.setSize(200, 200);
        System.out.println("Width when constrained to 200: " + p.getPreferredSize().width);
    }
}
