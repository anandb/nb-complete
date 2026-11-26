package github.anandb.netbeans.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Holiday decoration data for the assistant's support button.
 *
 * <p>{@value #RESOURCE} holds the date ranges, how many celebrations happen per day, how long
 * each lasts and the icons. While the IDE is active on a holiday day the button starts a
 * celebration at a random moment, holds it for {@code celebrationMinutes} from then, and does
 * so {@code celebrationsPerDay} times. Overlapping holidays are all candidates for each
 * celebration.</p>
 */
public final class HolidaySupport {

    /** Classpath resource holding the holiday calendar. */
    public static final String RESOURCE = "/github/anandb/netbeans/support/holiday-support.json";

    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private static final Logger LOG = Logger.from(HolidaySupport.class);

    /**
     * One holiday: the greeting shown in the tooltip, an inclusive month-day range and the
     * icons used during a celebration. Ranges may wrap across New Year when {@code start} is
     * after {@code end}.
     */
    public record Theme(MonthDay start, MonthDay end, String greeting, List<String> icons) {

        public Theme {
            if (greeting == null || greeting.isBlank()) {
                throw new IllegalArgumentException("A holiday needs a non-blank greeting.");
            }
            icons = List.copyOf(icons);
        }

        /** @return whether the given day falls inside this holiday's inclusive range. */
        public boolean contains(MonthDay day) {
            if (start.isBefore(end)) {
                return !day.isBefore(start) && !day.isAfter(end);
            }
            if (start.equals(end)) {
                return day.equals(start);
            }
            return !day.isBefore(start) || !day.isAfter(end);
        }
    }

    /** The greeting and icon of one celebration. */
    public record Celebration(String greeting, String iconName) { }

    private final int celebrationsPerDay;
    private final int celebrationMinutes;
    private final List<Theme> themes;

    /**
     * @param celebrationsPerDay how many celebrations a holiday day may have
     * @param celebrationMinutes how long each celebration lasts, measured from its start
     * @param themes holidays; when several match the same day, all are candidates
     */
    public HolidaySupport(int celebrationsPerDay, int celebrationMinutes, List<Theme> themes) {
        this.celebrationsPerDay = Math.max(0, celebrationsPerDay);
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
     * Picks the celebration for {@code date}: one of the covering holidays, then one of that
     * holiday's icons. {@code index} distinguishes the day's celebrations and {@code seed}
     * makes the choice vary between IDE sessions while staying stable for a celebration.
     *
     * @return the chosen celebration, or {@code null} when the date is not a holiday
     */
    public Celebration celebrationFor(LocalDate date, int index, long seed) {
        List<Theme> matching = themesOn(date);
        if (matching.isEmpty()) {
            return null;
        }
        long mixed = seed ^ (date.toEpochDay() * 0x9E3779B97F4A7C15L)
                ^ (index * 0xC2B2AE3D27D4EB4FL);
        Theme theme = matching.get(pick(mixed, matching.size()));
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

        int perDay = requireCount(root, "celebrationsPerDay", 1, 24);
        int minutes = requireCount(root, "celebrationMinutes", 1, 24 * 60);

        JsonNode holidaysNode = root.path("holidays");
        if (!holidaysNode.isArray() || holidaysNode.isEmpty()) {
            throw new IllegalArgumentException("\"holidays\" must be a non-empty array.");
        }
        List<Theme> themes = new ArrayList<>();
        for (int i = 0; i < holidaysNode.size(); i++) {
            themes.add(parseTheme(holidaysNode.get(i), i));
        }
        return new HolidaySupport(perDay, minutes, themes);
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
        MonthDay start = parseMonthDay(node.get("start"), where + ", \"start\"");
        MonthDay end = parseMonthDay(node.get("end"), where + ", \"end\"");
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
        return new Theme(start, end, greeting, icons);
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
        return new HolidaySupport(0, 60, List.of());
    }

    /** @return how many celebrations a holiday day may have */
    public int celebrationsPerDay() {
        return celebrationsPerDay;
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
