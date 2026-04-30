package github.anandb.netbeans.manager;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import github.anandb.netbeans.model.Session;

@ExtendWith(MockitoExtension.class)
class SessionManagerTest {

    @Mock
    private ProcessManager acpManager;

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() throws Exception {
        // Reset Singleton
        Field instanceField = SessionManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        sessionManager = SessionManager.getInstance();
        
        // Inject mock acpManager via reflection if needed, 
        // but SessionManager.getInstance() calls ACPManager.getInstance().
        // So we need to mock ACPManager singleton first.
        Field acpInstanceField = ProcessManager.class.getDeclaredField("instance");
        acpInstanceField.setAccessible(true);
        acpInstanceField.set(null, acpManager);
        
        // Ensure whenReady() doesn't return null
        when(acpManager.whenReady()).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void testCreateSession() {
        when(acpManager.createSession(anyString())).thenReturn(CompletableFuture.completedFuture(
            new Session("new-id", "New", null, "/dir", null, null, null, null)
        ));

        // createNewSession returns void, so we just verify it was called
        sessionManager.createNewSession("/dir");
        verify(acpManager).createSession("/dir");
    }
}
