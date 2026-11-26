package github.anandb.netbeans.ui.components;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openide.util.ImageUtilities;
import org.openide.windows.WindowManager;

import github.anandb.netbeans.support.HolidaySupport;
import github.anandb.netbeans.ui.rendering.ThemeManager;

/**
 * The support toolbar button. On a holiday day, while the IDE window is active, it starts a
 * celebration at a random moment: it swaps its sprout mark for a holiday icon and bounces
 * once, holds that icon and the holiday greeting for {@code celebrationMinutes}, then returns
 * to the sprout until the next celebration. Clicking still opens the support page after the
 * usual confirmation.
 *
 * <p>The calendar is loaded off the EDT. The decision timer is one-shot, armed only while the
 * button is displayable, and stopped in {@link #removeNotify()}.</p>
 */
public final class HolidaySupportButton extends JButton {

    private static final long serialVersionUID = 1L;

    /** One expected celebration start per 30 active minutes. */
    private static final int CHANCE_PER_TICK = 30;
    /** Pause after a celebration before the next one may start. */
    private static final int COOLDOWN_MINUTES = 60;
    /** Bounce: 30 frames × 40 ms = 1.2 s, two hops of 4 px. */
    private static final int BOUNCE_FRAME_MS = 40;
    private static final int BOUNCE_FRAMES = 30;
    private static final int BOUNCE_HOPS = 2;
    private static final int BOUNCE_PIXELS = 4;

    private final int iconSize;
    private final Icon baseIcon;
    private final String baseToolTip;
    private final BouncingIcon bouncing = new BouncingIcon();
    private final Timer eventTimer;
    private final Supplier<LocalDateTime> clock;
    private final BooleanSupplier ideActive;
    private final Random random;
    private final long sessionSeed = ThreadLocalRandom.current().nextLong();

    private HolidaySupport support;
    private String holidayIconName;
    private Timer bounceTimer;
    private LocalDateTime forcedUntil;
    private boolean forcePending;
    private LocalDate celebrationDay;
    private int celebrationsStarted;
    private LocalDateTime celebrationEnd;
    private LocalDateTime cooldownUntil;

    /**
     * @param baseIconName icon shown outside celebrations, e.g. {@code sprout.svg}
     * @param iconSize     toolbar icon edge in pixels
     * @param toolTip      tooltip shown outside celebrations
     * @param listener     click handler, may be {@code null}
     */
    public HolidaySupportButton(String baseIconName, int iconSize, String toolTip,
            ActionListener listener) {
        this(baseIconName, iconSize, toolTip, listener, null, LocalDateTime::now,
                new Random(), HolidaySupportButton::isIdeWindowActive);
    }

    /** Test seam: a preloaded calendar, clock, randomness and activation check. */
    HolidaySupportButton(String baseIconName, int iconSize, String toolTip,
            ActionListener listener, HolidaySupport calendar, Supplier<LocalDateTime> clock,
            Random random, BooleanSupplier ideActive) {
        this.iconSize = iconSize;
        this.baseToolTip = toolTip;
        this.clock = clock;
        this.random = random;
        this.ideActive = ideActive;
        this.baseIcon = ThemeManager.getIcon(baseIconName, iconSize);
        setIcon(baseIcon);
        if (baseIcon != null) {
            setDisabledIcon(ImageUtilities.createDisabledIcon(baseIcon));
        }
        setToolTipText(toolTip);
        getAccessibleContext().setAccessibleName(toolTip);
        getAccessibleContext().setAccessibleDescription(toolTip);
        UIUtils.styleToolbarButton(this);
        if (listener != null) {
            addActionListener(listener);
        }
        eventTimer = new Timer(60_000, e -> refreshState());
        eventTimer.setRepeats(false);
        if (calendar != null) {
            support = calendar;
        } else {
            CompletableFuture.supplyAsync(HolidaySupport::loadDefault).thenAccept(loaded ->
                    SwingUtilities.invokeLater(() -> {
                        support = loaded;
                        refreshState();
                        if (forcePending) {
                            forcePending = false;
                            forceCelebration();
                        }
                    }));
        }
    }

    /** Re-evaluates the state immediately; used when the assistant becomes active. */
    public void refreshNow() {
        refreshState();
    }

    void refreshState() {
        if (support == null) {
            return;
        }
        LocalDateTime now = clock.get();

        if (forcedUntil != null) {
            if (now.isBefore(forcedUntil)) {
                scheduleUntil(forcedUntil, now);
                return;
            }
            forcedUntil = null;
            showBase();
        }

        if (celebrationEnd != null) {
            if (now.isBefore(celebrationEnd)) {
                scheduleUntil(celebrationEnd, now);
                return;
            }
            celebrationEnd = null;
            showBase();
            cooldownUntil = now.plusMinutes(COOLDOWN_MINUTES);
        }

        LocalDate today = now.toLocalDate();
        if (!today.equals(celebrationDay)) {
            celebrationDay = today;
            celebrationsStarted = 0;
            cooldownUntil = null;
        }

        if (!support.isHoliday(today) || celebrationsStarted >= support.celebrationsPerDay()) {
            scheduleUntil(today.plusDays(1).atStartOfDay(), now);
            return;
        }

        if (cooldownUntil != null && now.isBefore(cooldownUntil)) {
            scheduleUntil(cooldownUntil, now);
            return;
        }

        if (!ideActive.getAsBoolean()) {
            scheduleUntil(now.plusMinutes(1), now);
            return;
        }

        if (random.nextInt(CHANCE_PER_TICK) == 0) {
            startCelebration(today, now);
            return;
        }
        scheduleUntil(now.plusMinutes(1), now);
    }

    private void startCelebration(LocalDate day, LocalDateTime now) {
        HolidaySupport.Celebration celebration =
                support.celebrationFor(day, celebrationsStarted, sessionSeed);
        if (celebration == null
                || !showCelebration(celebration.iconName(), celebration.greeting())) {
            scheduleUntil(now.plusMinutes(1), now);
            return;
        }
        celebrationsStarted++;
        celebrationEnd = now.plusMinutes(support.celebrationMinutes());
        scheduleUntil(celebrationEnd, now);
    }

    /**
     * Testing hook: decorates the button now with a randomly chosen configured holiday and
     * one of that holiday's icons, for one celebration period. Does nothing until the
     * calendar has loaded.
     */
    public void forceCelebration() {
        if (support == null) {
            forcePending = true;
            return;
        }
        LocalDateTime now = clock.get();
        HolidaySupport.Celebration celebration = support.randomCelebration();
        if (celebration == null
                || !showCelebration(celebration.iconName(), celebration.greeting())) {
            return;
        }
        forcedUntil = now.plusMinutes(support.celebrationMinutes());
        celebrationEnd = null;
        cooldownUntil = null;
        scheduleUntil(forcedUntil, now);
    }

    /** Applies the holiday icon, greeting and bounce; {@code false} when the icon is missing. */
    private boolean showCelebration(String iconName, String greeting) {
        Icon holidayIcon = ThemeManager.getIcon(iconName, iconSize);
        if (holidayIcon == null) {
            return false;
        }
        holidayIconName = iconName;
        setHolidayToolTip(greeting);
        bouncing.target = holidayIcon;
        bouncing.frame = 0;
        stopBounce();
        setIcon(bouncing);
        bounceTimer = new Timer(BOUNCE_FRAME_MS, e -> advanceBounce(holidayIcon));
        bounceTimer.start();
        return true;
    }

    private void advanceBounce(Icon holidayIcon) {
        bouncing.frame++;
        if (bouncing.frame >= BOUNCE_FRAMES) {
            stopBounce();
            setIcon(holidayIcon);
        } else {
            repaint();
        }
    }

    private void showBase() {
        stopBounce();
        holidayIconName = null;
        setIcon(baseIcon);
        setHolidayToolTip(null);
    }

    /** Shows the holiday greeting while decorating, or the original support tooltip otherwise. */
    private void setHolidayToolTip(String greeting) {
        String text = greeting == null ? baseToolTip : greeting;
        setToolTipText(text);
        getAccessibleContext().setAccessibleName(text);
        getAccessibleContext().setAccessibleDescription(text);
    }

    private void stopBounce() {
        if (bounceTimer != null) {
            bounceTimer.stop();
            bounceTimer = null;
        }
    }

    /** Arms the one-shot decision timer for {@code target}; no-op while not displayable. */
    private void scheduleUntil(LocalDateTime target, LocalDateTime now) {
        if (!isDisplayable()) {
            return;
        }
        long millis = Duration.between(now, target).toMillis() + 500L;
        millis = Math.max(1_000L, Math.min(millis, Integer.MAX_VALUE));
        eventTimer.stop();
        eventTimer.setInitialDelay((int) millis);
        eventTimer.start();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refreshState();
    }

    @Override
    public void removeNotify() {
        eventTimer.stop();
        stopBounce();
        if (holidayIconName != null) {
            Icon holidayIcon = ThemeManager.getIcon(holidayIconName, iconSize);
            if (holidayIcon != null) {
                setIcon(holidayIcon);
            }
        }
        super.removeNotify();
    }

    /** @return vertical lift in pixels for a bounce frame */
    private static int bounceLift(int frame) {
        if (frame <= 0 || frame >= BOUNCE_FRAMES) {
            return 0;
        }
        double progress = frame / (double) BOUNCE_FRAMES;
        double wave = Math.abs(Math.sin(Math.PI * BOUNCE_HOPS * progress));
        return (int) Math.round(BOUNCE_PIXELS * wave);
    }

    /** @return whether the main IDE window is the active OS window */
    private static boolean isIdeWindowActive() {
        Window window = WindowManager.getDefault().getMainWindow();
        return window == null || window.isActive();
    }

    /** Paints the holiday icon raised by the current bounce lift. */
    private static final class BouncingIcon implements Icon {

        private Icon target;
        private int frame;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            int lift = bounceLift(frame);
            g.translate(0, -lift);
            target.paintIcon(c, g, x, y);
            g.translate(0, lift);
        }

        @Override
        public int getIconWidth() {
            return target.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return target.getIconHeight();
        }
    }
}
