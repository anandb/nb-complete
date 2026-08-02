package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;

@ActionID(category = "Refactoring", id = "github.anandb.netbeans.ui.AskAssistantRefactorAction")
@ActionRegistration(displayName = "#CTL_AskAssistantRefactorAction")
@ActionReference(path = "Menu/Refactoring", position = 1860)
public class AskAssistantRefactorAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AssistantTarget.open();
    }
}
