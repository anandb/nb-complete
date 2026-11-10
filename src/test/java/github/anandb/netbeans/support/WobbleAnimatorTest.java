package github.anandb.netbeans.support;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WobbleAnimatorTest {

    @Test
    void startsWobbleOnceResetsRepeatsAndStops() throws Exception {
        AtomicInteger repaints = new AtomicInteger();
        WobbleAnimator animator = new WobbleAnimator(new TestComponent(repaints));
        animator.start();
        Timer firstRun = timer(animator, "wobbleTimer");
        animator.start();
        assertEquals(firstRun, timer(animator, "wobbleTimer"));

        fire(firstRun, 16);
        assertEquals(0, animator.x());
        assertEquals(0, animator.y());
        assertNull(timer(animator, "wobbleTimer"));
        Timer restart = timer(animator, "wobbleRestartTimer");
        assertNotNull(restart);
        fire(restart, 1);
        assertNotNull(timer(animator, "wobbleTimer"));

        fire(timer(animator, "wobbleTimer"), 1);
        animator.stop();
        assertNull(timer(animator, "wobbleTimer"));
        assertNull(timer(animator, "wobbleRestartTimer"));
        assertEquals(0, animator.x());
        assertEquals(0, animator.y());
        assertEquals(18, repaints.get());
    }

    @Test
    void scheduledStartCanBeCancelledOrFired() throws Exception {
        WobbleAnimator animator = new WobbleAnimator(new TestComponent(new AtomicInteger()));
        animator.scheduleStart();
        animator.scheduleStart();
        Timer delay = timer(animator, "wobbleDelayTimer");
        assertNotNull(delay);
        animator.cancelDelay();
        assertNull(timer(animator, "wobbleDelayTimer"));
        animator.scheduleStart();
        fire(timer(animator, "wobbleDelayTimer"), 1);
        assertNull(timer(animator, "wobbleDelayTimer"));
        assertNotNull(timer(animator, "wobbleTimer"));
        animator.cancelDelay();
        animator.stop();
    }

    @Test
    void buzzResetsOffsetsThenSchedulesGentleAttention() throws Exception {
        WobbleAnimator animator = new WobbleAnimator(new TestComponent(new AtomicInteger()));
        animator.start();
        animator.buzz();
        assertNull(timer(animator, "wobbleTimer"));
        Timer buzz = timer(animator, "buzzTimer");
        fire(buzz, 2);
        assertEquals(-3, animator.x());
        assertEquals(-2, animator.y());
        fire(buzz, 15);
        assertEquals(0, animator.x());
        assertEquals(0, animator.y());
        assertNull(timer(animator, "buzzTimer"));
        assertNotNull(timer(animator, "wobbleDelayTimer"));
        animator.stop();
    }

    private static void fire(Timer timer, int times) {
        timer.stop();
        ActionEvent event = new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, "test");
        for (int i = 0; i < times; i++) {
            for (ActionListener listener : timer.getActionListeners()) {
                listener.actionPerformed(event);
            }
        }
    }

    private static Timer timer(WobbleAnimator animator, String name) throws ReflectiveOperationException {
        Field field = WobbleAnimator.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Timer) field.get(animator);
    }

    private static final class TestComponent extends JComponent {
        private final AtomicInteger repaints;

        TestComponent(AtomicInteger repaints) {
            this.repaints = repaints;
        }

        @Override
        public void repaint() {
            if (repaints != null) {
                repaints.incrementAndGet();
            }
        }
    }
}
