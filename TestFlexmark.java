import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

public class TestFlexmark {
    public static void main(String[] args) {
        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();

        String rawText = "\n\n```java\npublic static boolean isPrime(int n) {\n    if (n< 2) return false;\n}\n";
        String html = renderer.render(parser.parse(rawText));
        System.out.println("Output:\n" + html);
    }
}
