package github.anandb.netbeans.ui.components;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openide.util.ImageUtilities;
import org.openide.windows.WindowManager;

import github.anandb.netbeans.support.HolidaySupport;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.rendering.ThemeManager;

/**
 * The support toolbar button. On a holiday date, while the IDE window is active, it starts
 * that holiday's celebration once a year at a random moment: it swaps its sprout mark for a
 * holiday icon and bounces once, holds that icon and the holiday greeting for
 * {@code celebrationMinutes}, then returns to the sprout. Clicking still opens the support
 * page after the usual confirmation.
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
    /** How long the greeting tooltip stays up after the icon switches. */
    private static final int GREETING_TOOLTIP_MS = 4_000;
    /** Gap between the button and the greeting tooltip above it; matches the paperclip. */
    private static final int GREETING_TOOLTIP_GAP = 8;

    private final int iconSize;
    private final Icon baseIcon;
    private final String baseToolTip;
    private final BouncingIcon bouncing = new BouncingIcon();
    private final Timer eventTimer;
    private final Supplier<LocalDateTime> clock;
    private final BooleanSupplier ideActive;
    private final Random random;
    private final long sessionSeed = ThreadLocalRandom.current().nextLong();
    private final PreferenceChangeListener settingsListener = this::onPreferenceChanged;

    private HolidaySupport support;
    private String holidayIconName;
    private Timer bounceTimer;
    private Popup greetingPopup;
    private Timer greetingPopupTimer;
    private LocalDateTime forcedUntil;
    private boolean forcePending;
    private LocalDateTime celebrationEnd;
    private LocalDateTime cooldownUntil;
    private int celebrationYear = Integer.MIN_VALUE;
    private final Set<MonthDay> celebratedDays = new HashSet<>();
    private boolean settingsListenerRegistered;

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

    @Override
    public Point getToolTipLocation(MouseEvent event) {
        return UIUtils.tooltipAboveButton(event, this);
    }

    /** Re-evaluates the state immediately; used when the assistant becomes active. */
    public void refreshNow() {
        refreshState();
    }

    void refreshState() {
        if (support == null) {
            return;
        }
        if (!PluginSettings.isHolidaySupportEnabled()) {
            disableCelebration();
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
        if (today.getYear() != celebrationYear) {
            celebrationYear = today.getYear();
            celebratedDays.clear();
            cooldownUntil = null;
        }

        if (!support.isHoliday(today) || celebratedDays.contains(MonthDay.from(today))) {
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
        HolidaySupport.Celebration celebration = support.celebrationFor(day, sessionSeed);
        if (celebration == null
                || !showCelebration(celebration.iconName(), celebration.greeting())) {
            scheduleUntil(now.plusMinutes(1), now);
            return;
        }
        celebratedDays.add(MonthDay.from(day));
        celebrationEnd = now.plusMinutes(support.celebrationMinutes());
        scheduleUntil(celebrationEnd, now);
    }

    /**
     * Testing hook: decorates the button now with a randomly chosen configured holiday and
     * one of that holiday's icons, for one celebration period. Does nothing until the
     * calendar has loaded.
     */
    public void forceCelebration() {
        if (!PluginSettings.isHolidaySupportEnabled()) {
            disableCelebration();
            return;
        }
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
        showGreetingToolTip(greeting);
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
        hideGreetingToolTip();
        holidayIconName = null;
        setIcon(baseIcon);
        setHolidayToolTip(null);
    }

    /** Cancels any celebration and restores the sprout when the option is turned off. */
    private void disableCelebration() {
        forcedUntil = null;
        forcePending = false;
        celebrationEnd = null;
        celebrationYear = Integer.MIN_VALUE;
        celebratedDays.clear();
        cooldownUntil = null;
        showBase();
    }

    private void onPreferenceChanged(PreferenceChangeEvent evt) {
        if (PreferenceKeys.HOLIDAY_SUPPORT_ENABLED.equals(evt.getKey())) {
            SwingUtilities.invokeLater(this::refreshState);
        }
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

    /**
     * Shows the greeting in a tooltip-style popup for four seconds. Swing shows tooltips only
     * on hover, so the icon switch would otherwise announce itself with no text at all.
     */
    private void showGreetingToolTip(String greeting) {
        hideGreetingToolTip();
        if (greeting == null || greeting.isBlank() || !isShowing()) {
            return;
        }
        JToolTip tip = createToolTip();
        tip.setTipText(greeting);
        Dimension size = tip.getPreferredSize();
        try {
            Point anchor = getLocationOnScreen();
            Rectangle screen = getGraphicsConfiguration().getBounds();
            int x = anchor.x + getInsets().left;
            x = Math.max(screen.x, Math.min(x, screen.x + screen.width - size.width));
            int y = anchor.y - size.height - GREETING_TOOLTIP_GAP;
            if (y < screen.y) {
                y = anchor.y + getHeight() + GREETING_TOOLTIP_GAP;
            }
            greetingPopup = PopupFactory.getSharedInstance().getPopup(this, tip, x, y);
            greetingPopup.show();
        } catch (IllegalComponentStateException ex) {
            greetingPopup = null;
            return;
        }
        greetingPopupTimer = new Timer(GREETING_TOOLTIP_MS, e -> hideGreetingToolTip());
        greetingPopupTimer.setRepeats(false);
        greetingPopupTimer.start();
    }

    /** Hides the greeting popup and stops its timer. */
    private void hideGreetingToolTip() {
        if (greetingPopupTimer != null) {
            greetingPopupTimer.stop();
            greetingPopupTimer = null;
        }
        if (greetingPopup != null) {
            greetingPopup.hide();
            greetingPopup = null;
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
        if (!settingsListenerRegistered) {
            PlatformBridge.prefStoreSafe().addChangeListener(settingsListener);
            settingsListenerRegistered = true;
        }
        refreshState();
    }

    @Override
    public void removeNotify() {
        if (settingsListenerRegistered) {
            PlatformBridge.prefStoreSafe().removeChangeListener(settingsListener);
            settingsListenerRegistered = false;
        }
        eventTimer.stop();
        stopBounce();
        hideGreetingToolTip();
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
