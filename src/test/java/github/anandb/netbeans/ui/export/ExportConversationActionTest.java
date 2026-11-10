package github.anandb.netbeans.ui.export;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.awt.event.ActionEvent;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import github.anandb.netbeans.ui.assistant.AssistantTopComponent;

class ExportConversationActionTest {

    @Test
    void delegatesToAssistantWhenItIsOpen() {
        AssistantTopComponent assistant = mock(AssistantTopComponent.class);
        try (MockedStatic<AssistantTopComponent> lookup = mockStatic(AssistantTopComponent.class)) {
            lookup.when(AssistantTopComponent::findInstance).thenReturn(assistant);

            new ExportConversationAction().actionPerformed(new ActionEvent(
                    this, ActionEvent.ACTION_PERFORMED, "export"));

            verify(assistant).exportConversation();
        }
    }

    @Test
    void doesNothingWhenAssistantIsNotOpen() {
        try (MockedStatic<AssistantTopComponent> lookup = mockStatic(AssistantTopComponent.class)) {
            lookup.when(AssistantTopComponent::findInstance).thenReturn(null);

            new ExportConversationAction().actionPerformed(new ActionEvent(
                    this, ActionEvent.ACTION_PERFORMED, "export"));

            lookup.verify(AssistantTopComponent::findInstance);
        }
    }
}
