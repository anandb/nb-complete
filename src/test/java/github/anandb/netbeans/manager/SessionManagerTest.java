package github.anandb.netbeans.manager;

import github.anandb.netbeans.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionManagerTest {

    @Mock
    private ACPManager acpManager;

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
        Field acpInstanceField = ACPManager.class.getDeclaredField("instance");
        acpInstanceField.setAccessible(true);
        acpInstanceField.set(null, acpManager);
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
