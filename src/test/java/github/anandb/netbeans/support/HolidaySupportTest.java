package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.openide.util.ImageUtilities;

class HolidaySupportTest {

    private static final List<String> THANKSGIVING_ICONS = List.of(
            "holidays/thanksgiving-pumpkin.svg",
            "holidays/thanksgiving-turkey.svg",
            "holidays/thanksgiving-leaf.svg",
            "holidays/thanksgiving-pie.svg",
            "holidays/thanksgiving-corn.svg",
            "holidays/thanksgiving-turkey-leg.svg");

    @Test
    void bundledCalendarDefinesAllHolidaysAndTwoDailyCelebrations() {
        HolidaySupport calendar = HolidaySupport.loadDefault();

        assertEquals(2, calendar.celebrationsPerDay());
        assertEquals(60, calendar.celebrationMinutes());
        assertEquals(21, calendar.themes().size());
        HolidaySupport.Theme thanksgiving = calendar.themes().stream()
                .filter(theme -> theme.greeting().equals("Happy Thanksgiving"))
                .findFirst().orElseThrow();
        assertEquals(THANKSGIVING_ICONS, thanksgiving.icons());
    }

    @Test
    void everyHolidayOffersAtLeastFourIcons() {
        for (HolidaySupport.Theme theme : HolidaySupport.loadDefault().themes()) {
            assertTrue(theme.icons().size() >= 4,
                    theme.greeting() + " has only " + theme.icons().size() + " icons");
        }
    }

    @Test
    void holidayRangesAreInclusiveAndYearAgnostic() {
        HolidaySupport calendar = HolidaySupport.loadDefault();

        assertTrue(calendar.isHoliday(LocalDate.of(2026, 11, 26)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 11, 29)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 11, 25)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 11, 30)));

        assertTrue(calendar.isHoliday(LocalDate.of(2026, 12, 5)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 12, 25)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 12, 4)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 12, 26)));

        assertTrue(calendar.isHoliday(LocalDate.of(2026, 10, 15)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 11, 15)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 10, 14)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 11, 16)));

        assertTrue(calendar.isHoliday(LocalDate.of(2026, 1, 1)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 1, 2)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 2, 14)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 3, 14)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 3, 25)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 4, 22)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 5, 1)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 5, 4)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 6, 21)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 7, 20)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 8, 12)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 9, 12)), "leap-year Programmer's Day");
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 9, 13)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 9, 14)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 10, 31)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 11, 3)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 11, 23)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 12, 10)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 12, 21)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 12, 23)));

        assertEquals(2, calendar.themesOn(LocalDate.of(2026, 10, 31)).size(),
                "Halloween overlaps Diwali");
        assertEquals(2, calendar.themesOn(LocalDate.of(2026, 12, 10)).size(),
                "Ada Lovelace Day overlaps Christmas");

        assertTrue(calendar.isHoliday(LocalDate.of(2027, 11, 26)), "ranges repeat each year");
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 7, 15)));
    }

    @Test
    void aRangeMayWrapAcrossNewYear() {
        HolidaySupport calendar = new HolidaySupport(2, 60, List.of(
                new HolidaySupport.Theme(MonthDay.of(12, 28), MonthDay.of(1, 3), "Happy New Year",
                        List.of("ny.svg"))));

        assertTrue(calendar.isHoliday(LocalDate.of(2027, 1, 1)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 12, 31)));
        assertFalse(calendar.isHoliday(LocalDate.of(2027, 1, 4)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 12, 27)));
    }

    @Test
    void celebrationUsesAConfiguredHolidayAndIcon() {
        HolidaySupport calendar = HolidaySupport.loadDefault();
        LocalDate thanksgiving = LocalDate.of(2026, 11, 27);

        HolidaySupport.Celebration celebration = calendar.celebrationFor(thanksgiving, 0, 42L);
        assertNotNull(celebration);
        assertEquals("Happy Thanksgiving", celebration.greeting());
        assertTrue(THANKSGIVING_ICONS.contains(celebration.iconName()));

        assertEquals(celebration, calendar.celebrationFor(thanksgiving, 0, 42L),
                "the same session and index keep the same choice");
        assertNull(calendar.celebrationFor(LocalDate.of(2026, 7, 15), 0, 42L));
    }

    @Test
    void overlappingHolidaysReachEveryCandidateAcrossSessions() {
        HolidaySupport calendar = new HolidaySupport(2, 60, List.of(
                new HolidaySupport.Theme(MonthDay.of(1, 1), MonthDay.of(12, 31),
                        "Holiday A", List.of("a1.svg", "a2.svg", "a3.svg")),
                new HolidaySupport.Theme(MonthDay.of(1, 1), MonthDay.of(12, 31),
                        "Holiday B", List.of("b1.svg", "b2.svg"))));
        LocalDate day = LocalDate.of(2026, 1, 15);

        Set<String> greetings = new HashSet<>();
        Set<String> icons = new HashSet<>();
        for (long seed = 0; seed < 40; seed++) {
            for (int index = 0; index < 2; index++) {
                HolidaySupport.Celebration celebration = calendar.celebrationFor(day, index, seed);
                assertNotNull(celebration);
                greetings.add(celebration.greeting());
                icons.add(celebration.iconName());
                Set<String> allowed = celebration.greeting().equals("Holiday A")
                        ? Set.of("a1.svg", "a2.svg", "a3.svg")
                        : Set.of("b1.svg", "b2.svg");
                assertTrue(allowed.contains(celebration.iconName()),
                        "icon must belong to the chosen holiday");
            }
        }
        assertEquals(Set.of("Holiday A", "Holiday B"), greetings);
        assertTrue(icons.size() >= 3, "several icons are reachable");
    }

    @Test
    void randomCelebrationKeepsGreetingAndIconFromTheSameHoliday() {
        HolidaySupport calendar = HolidaySupport.loadDefault();
        Map<String, Set<String>> iconsByGreeting = new HashMap<>();
        for (HolidaySupport.Theme theme : calendar.themes()) {
            iconsByGreeting.put(theme.greeting(), Set.copyOf(theme.icons()));
        }

        for (int i = 0; i < 50; i++) {
            HolidaySupport.Celebration celebration = calendar.randomCelebration();
            assertNotNull(celebration);
            assertTrue(iconsByGreeting.containsKey(celebration.greeting()));
            assertTrue(iconsByGreeting.get(celebration.greeting()).contains(celebration.iconName()),
                    celebration.greeting() + " got unrelated icon " + celebration.iconName());
        }
        assertNull(HolidaySupport.empty().randomCelebration());
    }

    @Test
    void everyConfiguredIconExistsAndLoads() {
        HolidaySupport calendar = HolidaySupport.loadDefault();

        for (HolidaySupport.Theme theme : calendar.themes()) {
            for (String icon : theme.icons()) {
                String path = "github/anandb/netbeans/ui/icons/" + icon;
                URL resource = HolidaySupport.class.getClassLoader().getResource(path);
                assertNotNull(resource, "missing icon resource: " + icon);
                assertNotNull(ImageUtilities.loadImage(path, true), "icon does not load: " + icon);
            }
        }
    }

    @Test
    void emptyCalendarNeverCelebrates() {
        HolidaySupport empty = HolidaySupport.empty();

        assertFalse(empty.isHoliday(LocalDate.of(2026, 12, 25)));
        assertNull(empty.celebrationFor(LocalDate.of(2026, 12, 25), 0, 1L));
        assertEquals(0, empty.celebrationsPerDay());
    }

    @Test
    void parseReadsCountsAndHolidays() {
        HolidaySupport calendar = HolidaySupport.parse("""
                {"celebrationsPerDay":2,"celebrationMinutes":45,
                 "holidays":[{"greeting":"Test","start":"03-01","end":"03-02","icons":["a.svg"]}]}
                """);

        assertEquals(2, calendar.celebrationsPerDay());
        assertEquals(45, calendar.celebrationMinutes());
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 3, 1)));
    }

    @Test
    void rejectsMalformedCalendars() {
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("{}"));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationMinutes":60,"holidays":[]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationsPerDay":0,"celebrationMinutes":60,"holidays":[]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationsPerDay":2,"celebrationMinutes":0,"holidays":[]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationsPerDay":2,"celebrationMinutes":60,"holidays":[]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationsPerDay":2,"celebrationMinutes":60,"holidays":[
                  {"start":"12-01","end":"12-02","icons":["a.svg"]}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationsPerDay":2,"celebrationMinutes":60,"holidays":[
                  {"greeting":"Test","start":"12-01","end":"12-02","icons":[]}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationsPerDay":2,"celebrationMinutes":60,"holidays":[
                  {"greeting":"Test","start":"13-01","end":"12-02","icons":["a.svg"]}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationsPerDay":2,"celebrationMinutes":60,"holidays":[
                  {"greeting":"Test","start":"12/01","end":"12-02","icons":["a.svg"]}]}
                """));
    }
}
