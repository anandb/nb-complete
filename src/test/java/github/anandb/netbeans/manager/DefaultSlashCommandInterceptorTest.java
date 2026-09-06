package github.anandb.netbeans.manager;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.SlashCommandCallback;
import github.anandb.netbeans.contract.SlashCommandHandler;
import github.anandb.netbeans.contract.SlashCommandProvider;
import github.anandb.netbeans.model.ModelRecords.CommandInfo;
import github.anandb.netbeans.support.LookupProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.util.Lookup;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultSlashCommandInterceptorTest {

    private DefaultSlashCommandInterceptor interceptor;
    private SlashCommandCallback callback;

    @BeforeEach
    void setUp() {
        LookupProvider.setOverride(null);
        interceptor = new DefaultSlashCommandInterceptor();
        callback = mock(SlashCommandCallback.class);
        interceptor.setCallback(callback);
    }

    @AfterEach
    void tearDown() {
        LookupProvider.setOverride(null);
    }

    @Test
    void testDefaultCommandsRegistered() {
        Map<String, CommandInfo> commands = interceptor.getCommands();
        assertNotNull(commands);
        assertTrue(commands.containsKey("/model"));
        assertTrue(commands.containsKey("/models"));
        assertTrue(commands.containsKey("/agent"));
        assertTrue(commands.containsKey("/agents"));
        assertTrue(commands.containsKey("/new"));
        assertTrue(commands.containsKey("/session"));
        assertTrue(commands.containsKey("/sessions"));
        assertTrue(commands.containsKey("/archive"));
        assertTrue(commands.containsKey("/level"));
        assertTrue(commands.containsKey("/levels"));
        assertTrue(commands.containsKey("/title"));
        assertTrue(commands.containsKey("/compact"));
    }

    @Test
    void testInterceptReturnsFalseForNullText() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept(null, context);
        assertFalse(result.get());
    }

    @Test
    void testInterceptReturnsFalseForNonSlashCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("hello world", context);
        assertFalse(result.get());
    }

    @Test
    void testInterceptReturnsFalseForUnknownCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/unknown", context);
        assertFalse(result.get());
    }

    @Test
    void testInterceptHandlesModelCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/model", context);
        assertTrue(result.get());
        verify(callback).popupModelCombo();
    }

    @Test
    void testInterceptHandlesModelsCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/models", context);
        assertTrue(result.get());
        verify(callback).popupModelCombo();
    }

    @Test
    void testInterceptHandlesAgentCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/agent", context);
        assertTrue(result.get());
        verify(callback).popupAgentCombo();
    }

    @Test
    void testInterceptHandlesAgentsCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/agents", context);
        assertTrue(result.get());
        verify(callback).popupAgentCombo();
    }

    @Test
    void testInterceptHandlesNewCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/new", context);
        assertTrue(result.get());
        verify(callback).popupNewSession();
    }

    @Test
    void testInterceptHandlesSessionCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/session", context);
        assertTrue(result.get());
        verify(callback).popupSessionCombo();
    }

    @Test
    void testInterceptHandlesSessionsCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/sessions", context);
        assertTrue(result.get());
        verify(callback).popupSessionCombo();
    }

    @Test
    void testInterceptHandlesArchiveCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/archive", context);
        assertTrue(result.get());
        verify(callback).popupArchiveSession();
    }

    @Test
    void testInterceptHandlesLevelCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/level", context);
        assertTrue(result.get());
        verify(callback).popupThinkingCombo();
    }

    @Test
    void testInterceptHandlesLevelsCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/levels", context);
        assertTrue(result.get());
        verify(callback).popupThinkingCombo();
    }

    @Test
    void testInterceptHandlesCompactCommand() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/compact", context);
        // /compact is passthrough - returns false to let server handle it
        assertFalse(result.get());
        // No callback invoked for passthrough
        verifyNoInteractions(callback);
    }

    @Test
    void testInterceptHandlesCommandWithArgs() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/model gpt-4", context);
        assertTrue(result.get());
        verify(callback).popupModelCombo();
    }

    @Test
    void testInterceptHandlesCommandWithLeadingWhitespace() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("  /model", context);
        assertTrue(result.get());
        verify(callback).popupModelCombo();
    }

    @Test
    void testSetCallback() {
        SlashCommandCallback newCallback = mock(SlashCommandCallback.class);
        interceptor.setCallback(newCallback);
        assertEquals(newCallback, interceptor.getCallback());
    }

    @Test
    void testGetCallbackReturnsNullByDefault() {
        DefaultSlashCommandInterceptor freshInterceptor = new DefaultSlashCommandInterceptor();
        assertNull(freshInterceptor.getCallback());
    }

    @Test
    void testRegisterCustomCommand() throws Exception {
        var handler = mock(SlashCommandHandler.class);
        when(handler.handle(anyString(), any(Lookup.class)))
                .thenReturn(CompletableFuture.completedFuture(true));

        interceptor.registerCommand("/custom", handler, "Custom command");

        Map<String, CommandInfo> commands = interceptor.getCommands();
        assertTrue(commands.containsKey("/custom"));

        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/custom arg1", context);
        assertTrue(result.get());
    }

    @Test
    void testCommandIsCaseSensitive() throws Exception {
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/Model", context);
        assertFalse(result.get());
    }

    @Test
    void testTitleCommandWithArgsRenamesSession() throws Exception {
        SessionControl sessionControl = mock(SessionControl.class);
        when(sessionControl.getCurrentSessionId()).thenReturn("test-session-id");

        Lookup context = mock(Lookup.class);
        when(context.lookup(SessionControl.class)).thenReturn(sessionControl);

        CompletableFuture<Boolean> result = interceptor.intercept("/title My New Title", context);
        assertTrue(result.get());

        verify(sessionControl).renameSession("test-session-id", "My New Title");
        verify(callback).displayToolMessage("title", "Renamed to: My New Title");
    }

    @Test
    void testTitleCommandWithoutArgsReturnsFalseWhenNoSession() throws Exception {
        SessionControl sessionControl = mock(SessionControl.class);
        when(sessionControl.getCurrentSessionId()).thenReturn(null);

        Lookup context = mock(Lookup.class);
        when(context.lookup(SessionControl.class)).thenReturn(sessionControl);

        CompletableFuture<Boolean> result = interceptor.intercept("/title", context);
        assertFalse(result.get());
    }

    @Test
    void testTitleCommandWithoutArgsReturnsFalseWhenNoSessionControl() throws Exception {
        Lookup context = mock(Lookup.class);
        when(context.lookup(SessionControl.class)).thenReturn(null);

        CompletableFuture<Boolean> result = interceptor.intercept("/title", context);
        assertFalse(result.get());
    }

    @Test
    void testCommandsMapIsUnmodifiable() {
        Map<String, CommandInfo> commands = interceptor.getCommands();
        assertThrows(UnsupportedOperationException.class, () -> {
            commands.put("/new-command", null);
        });
    }

    // Null callback tests - commands should work without NPE when callback is null

    @Test
    void testModelCommandWithNullCallback() throws Exception {
        interceptor.setCallback(null);
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/model", context);
        assertTrue(result.get());
    }

    @Test
    void testAgentCommandWithNullCallback() throws Exception {
        interceptor.setCallback(null);
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/agent", context);
        assertTrue(result.get());
    }

    @Test
    void testLevelCommandWithNullCallback() throws Exception {
        interceptor.setCallback(null);
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/level", context);
        assertTrue(result.get());
    }

    @Test
    void testSessionCommandWithNullCallback() throws Exception {
        interceptor.setCallback(null);
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/session", context);
        assertTrue(result.get());
    }

    @Test
    void testArchiveCommandWithNullCallback() throws Exception {
        interceptor.setCallback(null);
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/archive", context);
        assertTrue(result.get());
    }

    @Test
    void testNewCommandWithNullCallback() throws Exception {
        interceptor.setCallback(null);
        Lookup context = Lookup.getDefault();
        CompletableFuture<Boolean> result = interceptor.intercept("/new", context);
        assertTrue(result.get());
    }

    @Test
    void testTitleCommandWithNullCallbackAndArgs() throws Exception {
        interceptor.setCallback(null);
        SessionControl sessionControl = mock(SessionControl.class);
        when(sessionControl.getCurrentSessionId()).thenReturn("test-session-id");

        Lookup context = mock(Lookup.class);
        when(context.lookup(SessionControl.class)).thenReturn(sessionControl);

        CompletableFuture<Boolean> result = interceptor.intercept("/title My Title", context);
        assertTrue(result.get());

        verify(sessionControl).renameSession("test-session-id", "My Title");
    }

    // SlashCommandProvider discovery tests

    @Test
    void testProviderCommandRegistered() {
        SlashCommandHandler handler = mock(SlashCommandHandler.class);
        when(handler.handle(anyString(), any(Lookup.class)))
                .thenReturn(CompletableFuture.completedFuture(true));

        SlashCommandProvider provider = mock(SlashCommandProvider.class);
        when(provider.getCommand()).thenReturn("/provided-cmd");
        when(provider.getHandler()).thenReturn(handler);
        when(provider.getDescription()).thenReturn("Provider command");

        Lookup mockLookup = mock(Lookup.class);
        when(mockLookup.lookup(SlashCommandProvider.class)).thenReturn(provider);
        LookupProvider.setOverride(mockLookup);

        DefaultSlashCommandInterceptor freshInterceptor = new DefaultSlashCommandInterceptor();

        Map<String, CommandInfo> commands = freshInterceptor.getCommands();
        assertTrue(commands.containsKey("/provided-cmd"));
    }

    @Test
    void testProviderWithNullCommandIgnored() {
        SlashCommandProvider provider = mock(SlashCommandProvider.class);
        when(provider.getCommand()).thenReturn(null);
        when(provider.getHandler()).thenReturn(mock(SlashCommandHandler.class));

        Lookup mockLookup = mock(Lookup.class);
        when(mockLookup.lookup(SlashCommandProvider.class)).thenReturn(provider);
        LookupProvider.setOverride(mockLookup);

        DefaultSlashCommandInterceptor freshInterceptor = new DefaultSlashCommandInterceptor();
        assertNotNull(freshInterceptor.getCommands());
    }

    @Test
    void testProviderWithNullHandlerIgnored() {
        SlashCommandProvider provider = mock(SlashCommandProvider.class);
        when(provider.getCommand()).thenReturn("/cmd-no-handler");
        when(provider.getHandler()).thenReturn(null);

        Lookup mockLookup = mock(Lookup.class);
        when(mockLookup.lookup(SlashCommandProvider.class)).thenReturn(provider);
        LookupProvider.setOverride(mockLookup);

        DefaultSlashCommandInterceptor freshInterceptor = new DefaultSlashCommandInterceptor();
        assertFalse(freshInterceptor.getCommands().containsKey("/cmd-no-handler"));
    }
}
