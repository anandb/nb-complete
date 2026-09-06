package github.anandb.netbeans.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestRejectedExceptionTest {

    @Test
    void carriesMessage() {
        RequestRejectedException ex = new RequestRejectedException("nope");
        assertEquals("nope", ex.getMessage());
    }

    @Test
    void isRuntimeException() {
        RequestRejectedException ex = new RequestRejectedException("err");
        assertThrows(RuntimeException.class, () -> { throw ex; });
    }

    @Test
    void nullMessageAllowed() {
        RequestRejectedException ex = new RequestRejectedException(null);
        assertNull(ex.getMessage());
    }
}
