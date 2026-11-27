package github.anandb.netbeans.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Holiday decoration data for the assistant's support button.
 *
 * <p>{@value #RESOURCE} holds each holiday's date, how long a celebration lasts and its
 * icons. While the IDE is active on a holiday the button starts one celebration at a random
 * moment, holds it for {@code celebrationMinutes} from then, and shows that holiday only once
 * per year. Each date belongs to exactly one holiday.</p>
 */
public final class HolidaySupport {

    /** Classpath resource holding the holiday calendar. */
    public static final String RESOURCE = "/github/anandb/netbeans/support/holiday-support.json";

    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private static final Logger LOG = Logger.from(HolidaySupport.class);

    /**
     * One holiday: the greeting shown in the tooltip, its month-day date and the icons used
     * during a celebration.
     */
    public record Theme(MonthDay date, String greeting, List<String> icons) {

        public Theme {
            if (greeting == null || greeting.isBlank()) {
                throw new IllegalArgumentException("A holiday needs a non-blank greeting.");
            }
            icons = List.copyOf(icons);
        }

        /** @return whether this holiday falls on {@code day} */
        public boolean contains(MonthDay day) {
            return date.equals(day);
        }
    }

    /** The greeting and icon of one celebration. */
    public record Celebration(String greeting, String iconName) { }

    private final int celebrationMinutes;
    private final List<Theme> themes;

    /**
     * @param celebrationMinutes how long each celebration lasts, measured from its start
     * @param themes holidays; dates are unique, one holiday per day
     */
    public HolidaySupport(int celebrationMinutes, List<Theme> themes) {
        this.celebrationMinutes = Math.max(1, celebrationMinutes);
        this.themes = List.copyOf(themes);
    }

    /** @return whether any theme covers {@code date} */
    public boolean isHoliday(LocalDate date) {
        return !themesOn(date).isEmpty();
    }

    /** @return the holidays covering {@code date}, in configured order */
    public List<Theme> themesOn(LocalDate date) {
        MonthDay day = MonthDay.from(date);
        List<Theme> matching = new ArrayList<>();
        for (Theme theme : themes) {
            if (theme.contains(day)) {
                matching.add(theme);
            }
        }
        return List.copyOf(matching);
    }

    /**
     * Picks the celebration for {@code date}: the day's holiday, then one of its icons.
     * {@code seed} makes the choice vary between IDE sessions while staying stable for a
     * celebration.
     *
     * @return the chosen celebration, or {@code null} when the date is not a holiday
     */
    public Celebration celebrationFor(LocalDate date, long seed) {
        List<Theme> matching = themesOn(date);
        if (matching.isEmpty()) {
            return null;
        }
        long mixed = seed ^ (date.toEpochDay() * 0x9E3779B97F4A7C15L);
        Theme theme = matching.get(0);
        String icon = theme.icons().get(pick(mixed * 131 + theme.greeting().hashCode(),
                theme.icons().size()));
        return new Celebration(theme.greeting(), icon);
    }

    /** Maps a seed onto an index; consecutive inputs spread across the list. */
    private static int pick(long seed, int size) {
        long mixed = seed * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 32;
        return (int) Math.floorMod(mixed, size);
    }

    /**
     * Picks a celebration at random from all configured holidays, ignoring dates. The icon
     * always comes from the same holiday as the greeting. Used by the testing hook.
     *
     * @return a celebration, or {@code null} when the calendar is empty
     */
    public Celebration randomCelebration() {
        if (themes.isEmpty()) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Theme theme = themes.get(random.nextInt(themes.size()));
        return new Celebration(theme.greeting(),
                theme.icons().get(random.nextInt(theme.icons().size())));
    }

    /**
     * Parses the holiday calendar.
     *
     * @throws IllegalArgumentException when the document is not a valid calendar
     */
    public static HolidaySupport parse(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid holiday calendar JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("The holiday calendar must be a JSON object.");
        }

        int minutes = requireCount(root, "celebrationMinutes", 1, 24 * 60);

        JsonNode holidaysNode = root.path("holidays");
        if (!holidaysNode.isArray() || holidaysNode.isEmpty()) {
            throw new IllegalArgumentException("\"holidays\" must be a non-empty array.");
        }
        List<Theme> themes = new ArrayList<>();
        Set<MonthDay> dates = new HashSet<>();
        for (int i = 0; i < holidaysNode.size(); i++) {
            Theme theme = parseTheme(holidaysNode.get(i), i);
            if (!dates.add(theme.date())) {
                throw new IllegalArgumentException(
                        "Holiday date " + theme.date() + " is used by more than one holiday.");
            }
            themes.add(theme);
        }
        return new HolidaySupport(minutes, themes);
    }

    private static int requireCount(JsonNode root, String field, int min, int max) {
        JsonNode node = root.get(field);
        if (node == null || !node.isInt() || node.asInt() < min || node.asInt() > max) {
            throw new IllegalArgumentException(
                    "\"" + field + "\" must be an integer from " + min + " to " + max + ".");
        }
        return node.asInt();
    }

    private static Theme parseTheme(JsonNode node, int index) {
        String where = "Holiday #" + (index + 1);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(where + " must be a JSON object.");
        }
        String greeting = node.path("greeting").asText("");
        if (greeting.isBlank()) {
            throw new IllegalArgumentException(where + " needs a non-blank \"greeting\".");
        }
        where = where + " (" + greeting + ")";
        MonthDay date = parseMonthDay(node.get("date"), where + ", \"date\"");
        JsonNode iconsNode = node.path("icons");
        if (!iconsNode.isArray() || iconsNode.isEmpty()) {
            throw new IllegalArgumentException(where + " needs a non-empty \"icons\" array.");
        }
        List<String> icons = new ArrayList<>();
        for (JsonNode icon : iconsNode) {
            String iconName = icon.asText("");
            if (iconName.isBlank()) {
                throw new IllegalArgumentException(where + " contains a blank icon name.");
            }
            icons.add(iconName);
        }
        return new Theme(date, greeting, icons);
    }

    private static MonthDay parseMonthDay(JsonNode node, String where) {
        String text = node == null ? "" : node.asText("");
        int dash = text.indexOf('-');
        try {
            return MonthDay.of(
                    Integer.parseInt(text.substring(0, dash)),
                    Integer.parseInt(text.substring(dash + 1)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    where + " must use \"MM-dd\" (got \"" + text + "\").", e);
        }
    }

    /**
     * Loads the bundled calendar. Reads the classpath resource, so call it off the EDT.
     *
     * @return the parsed calendar, or an empty calendar when the resource is missing or invalid
     */
    public static HolidaySupport loadDefault() {
        try (InputStream in = HolidaySupport.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOG.severe("Holiday calendar not found: {0}", RESOURCE);
                return empty();
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            LOG.warn("Failed to load holiday calendar {0}", RESOURCE, e);
            return empty();
        }
    }

    /** @return a calendar with no holidays, used when the bundled data cannot be read. */
    public static HolidaySupport empty() {
        return new HolidaySupport(60, List.of());
    }

    /** @return how long each celebration lasts, in minutes */
    public int celebrationMinutes() {
        return celebrationMinutes;
    }

    /** @return the configured holidays, for diagnostics and tests */
    public List<Theme> themes() {
        return themes;
    }
}
