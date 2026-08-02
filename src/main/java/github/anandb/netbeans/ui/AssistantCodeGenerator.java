package github.anandb.netbeans.ui;

import java.util.Collections;
import java.util.List;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.codegen.CodeGenerator;
import org.openide.util.Lookup;

public class AssistantCodeGenerator implements CodeGenerator {

    private AssistantCodeGenerator(Lookup context) {
    }

    @MimeRegistration(mimeType = "", service = CodeGenerator.Factory.class, position = Integer.MAX_VALUE)
    public static class Factory implements CodeGenerator.Factory {
        @Override
        public List<? extends CodeGenerator> create(Lookup context) {
            JTextComponent component = context.lookup(JTextComponent.class);
            if (component == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(new AssistantCodeGenerator(context));
        }
    }

    @Override
    public String getDisplayName() {
        return "\uD83D\uDCAC Ask Assistant...";
    }

    @Override
    public void invoke() {
        AssistantTarget.open();
    }
}
