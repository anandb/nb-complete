package github.anandb.netbeans.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.Icon;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.support.HolidaySupport;
import github.anandb.netbeans.support.PluginSettings;

class HolidaySupportButtonTest {

    private static final String TOOLTIP = "Support this Project";
    private static final LocalDateTime THANKSGIVING =
            LocalDateTime.of(2026, 11, 26, 10, 30);

    @Test
    void celebrationStartsAtRandomTimeAndLastsItsConfiguredMinutes() {
        AtomicReference<LocalDateTime> now = new AtomicReference<>(THANKSGIVING);
        HolidaySupportButton button = button(thanksgivingCalendar(), now,
                alwaysZero(), new AtomicBoolean(true));

        Icon sprout = button.getIcon();
        assertEquals(TOOLTIP, button.getToolTipText());

        button.refreshState();
        assertNotSame(sprout, button.getIcon(), "active IDE starts a celebration");
        assertEquals("Happy Thanksgiving", button.getToolTipText());

        now.set(THANKSGIVING.plusMinutes(59));
        button.refreshState();
        assertNotSame(sprout, button.getIcon(), "the celebration holds for the hour");

        now.set(THANKSGIVING.plusMinutes(61));
        button.refreshState();
        assertSame(sprout, button.getIcon(), "the hour ends");
        assertEquals(TOOLTIP, button.getToolTipText());

        button.removeNotify();
    }

    @Test
    void oneCelebrationPerHolidayThenNoMore() {
        AtomicReference<LocalDateTime> now = new AtomicReference<>(THANKSGIVING);
        HolidaySupportButton button = button(thanksgivingCalendar(), now,
                alwaysZero(), new AtomicBoolean(true));

        Icon sprout = button.getIcon();
        button.refreshState();
        assertNotSame(sprout, button.getIcon(), "the holiday's celebration starts");

        now.set(THANKSGIVING.plusMinutes(61));
        button.refreshState();
        assertSame(sprout, button.getIcon(), "the celebration ended");

        now.set(THANKSGIVING.plusMinutes(150));
        button.refreshState();
        assertSame(sprout, button.getIcon(), "the holiday is not celebrated twice");
        assertEquals(TOOLTIP, button.getToolTipText());

        now.set(LocalDateTime.of(2026, 11, 27, 12, 0));
        button.refreshState();
        assertSame(sprout, button.getIcon(), "the next day is not a holiday");

        now.set(LocalDateTime.of(2027, 11, 26, 10, 30));
        button.refreshState();
        assertNotSame(sprout, button.getIcon(), "the holiday can celebrate again next year");

        button.removeNotify();
    }

    @Test
    void anInactiveIdeDoesNotStartACelebration() {
        AtomicReference<LocalDateTime> now = new AtomicReference<>(THANKSGIVING);
        AtomicBoolean active = new AtomicBoolean(false);
        HolidaySupportButton button = button(thanksgivingCalendar(), now,
                alwaysZero(), active);

        Icon sprout = button.getIcon();
        button.refreshState();
        assertSame(sprout, button.getIcon(), "no celebration while the IDE is inactive");
        assertEquals(TOOLTIP, button.getToolTipText());

        active.set(true);
        button.refreshState();
        assertNotSame(sprout, button.getIcon(), "activation allows the celebration to start");

        button.removeNotify();
    }

    @Test
    void forcedCelebrationLastsItsConfiguredMinutesThenRestoresTheSprout() {
        AtomicReference<LocalDateTime> now =
                new AtomicReference<>(LocalDateTime.of(2026, 7, 1, 12, 0));
        HolidaySupportButton button = button(thanksgivingCalendar(), now,
                alwaysZero(), new AtomicBoolean(true));

        Icon sprout = button.getIcon();
        button.forceCelebration();
        assertNotSame(sprout, button.getIcon(), "forcing decorates immediately");
        assertEquals("Happy Thanksgiving", button.getToolTipText());

        now.set(LocalDateTime.of(2026, 7, 1, 12, 30));
        button.refreshState();
        assertNotSame(sprout, button.getIcon(), "forced decoration survives state checks");

        now.set(LocalDateTime.of(2026, 7, 1, 13, 1));
        button.refreshState();
        assertSame(sprout, button.getIcon(), "the forced celebration ends");
        assertEquals(TOOLTIP, button.getToolTipText());

        button.removeNotify();
    }

    @Test
    void disabledOptionSuppressesAutomaticAndForcedCelebrations() {
        PluginSettings.setHolidaySupportEnabled(false);
        AtomicReference<LocalDateTime> now = new AtomicReference<>(THANKSGIVING);
        HolidaySupportButton button = button(thanksgivingCalendar(), now,
                alwaysZero(), new AtomicBoolean(true));
        try {
            Icon sprout = button.getIcon();

            button.refreshState();
            assertSame(sprout, button.getIcon(), "no automatic celebration when disabled");
            assertEquals(TOOLTIP, button.getToolTipText());

            button.forceCelebration();
            assertSame(sprout, button.getIcon(), "the test hook respects the option");
            assertEquals(TOOLTIP, button.getToolTipText());
        } finally {
            PluginSettings.setHolidaySupportEnabled(true);
            button.removeNotify();
        }
    }

    private static HolidaySupportButton button(HolidaySupport calendar,
            AtomicReference<LocalDateTime> now, Random random, AtomicBoolean active) {
        return new HolidaySupportButton("sprout.svg", 24, TOOLTIP, null, calendar,
                now::get, random, active::get);
    }

    private static HolidaySupport thanksgivingCalendar() {
        return new HolidaySupport(60, List.of(
                new HolidaySupport.Theme(MonthDay.of(11, 26),
                        "Happy Thanksgiving", List.of("holidays/thanksgiving-pumpkin.svg"))));
    }

    private static Random alwaysZero() {
        return new Random() {
            private static final long serialVersionUID = 1L;

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }
}
