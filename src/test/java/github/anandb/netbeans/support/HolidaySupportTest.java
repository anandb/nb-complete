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
    void bundledCalendarDefinesEachHolidayOnItsDate() {
        HolidaySupport calendar = HolidaySupport.loadDefault();

        assertEquals(60, calendar.celebrationMinutes());
        assertEquals(31, calendar.themes().size());
        HolidaySupport.Theme thanksgiving = calendar.themes().stream()
                .filter(theme -> theme.greeting().equals("Happy Thanksgiving"))
                .findFirst().orElseThrow();
        assertEquals(THANKSGIVING_ICONS, thanksgiving.icons());
        assertEquals(MonthDay.of(11, 26), thanksgiving.date());

        HolidaySupport.Theme christmas = calendar.themes().stream()
                .filter(theme -> theme.greeting().equals("Merry Christmas"))
                .findFirst().orElseThrow();
        assertEquals(MonthDay.of(12, 25), christmas.date());
    }

    @Test
    void everyHolidayOffersAtLeastFourIcons() {
        for (HolidaySupport.Theme theme : HolidaySupport.loadDefault().themes()) {
            assertTrue(theme.icons().size() >= 4,
                    theme.greeting() + " has only " + theme.icons().size() + " icons");
        }
    }

    @Test
    void holidayDatesAreExactAndYearAgnostic() {
        HolidaySupport calendar = HolidaySupport.loadDefault();

        assertTrue(calendar.isHoliday(LocalDate.of(2026, 11, 26)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 11, 25)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 11, 27)));

        assertTrue(calendar.isHoliday(LocalDate.of(2026, 12, 25)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 12, 24)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 12, 26)));

        assertTrue(calendar.isHoliday(LocalDate.of(2026, 10, 29)), "Diwali");
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 10, 28)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 10, 30)));

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
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 9, 13)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 9, 12)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 10, 31)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 11, 3)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 11, 23)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 12, 10)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 12, 21)));
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 12, 23)));

        assertEquals(1, calendar.themesOn(LocalDate.of(2026, 10, 31)).size(),
                "each date belongs to one holiday");
        assertTrue(calendar.isHoliday(LocalDate.of(2027, 11, 26)), "dates repeat each year");
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 7, 15)));
    }

    @Test
    void celebrationUsesTheDaysHolidayAndIcon() {
        HolidaySupport calendar = HolidaySupport.loadDefault();
        LocalDate thanksgiving = LocalDate.of(2026, 11, 26);

        HolidaySupport.Celebration celebration = calendar.celebrationFor(thanksgiving, 42L);
        assertNotNull(celebration);
        assertEquals("Happy Thanksgiving", celebration.greeting());
        assertTrue(THANKSGIVING_ICONS.contains(celebration.iconName()));

        assertEquals(celebration, calendar.celebrationFor(thanksgiving, 42L),
                "the same session keeps the same choice");
        assertNull(calendar.celebrationFor(LocalDate.of(2026, 7, 15), 42L));
    }

    @Test
    void duplicateHolidayDatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationMinutes":60,"holidays":[
                  {"greeting":"A","date":"01-01","icons":["a.svg"]},
                  {"greeting":"B","date":"01-01","icons":["b.svg"]}]}
                """));
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

        assertTrue(empty.themes().isEmpty());
        assertFalse(empty.isHoliday(LocalDate.of(2026, 12, 25)));
        assertNull(empty.celebrationFor(LocalDate.of(2026, 12, 25), 1L));
    }

    @Test
    void parseReadsCountsAndHolidays() {
        HolidaySupport calendar = HolidaySupport.parse("""
                {"celebrationMinutes":45,
                 "holidays":[{"greeting":"Test","date":"03-01","icons":["a.svg"]}]}
                """);

        assertEquals(45, calendar.celebrationMinutes());
        assertTrue(calendar.isHoliday(LocalDate.of(2026, 3, 1)));
        assertFalse(calendar.isHoliday(LocalDate.of(2026, 3, 2)));
    }

    @Test
    void rejectsMalformedCalendars() {
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("{}"));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationMinutes":60,"holidays":[]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationMinutes":0,"holidays":[]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationMinutes":60,"holidays":[
                  {"date":"12-01","icons":["a.svg"]}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationMinutes":60,"holidays":[
                  {"greeting":"Test","icons":["a.svg"]}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationMinutes":60,"holidays":[
                  {"greeting":"Test","date":"12-01","icons":[]}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationMinutes":60,"holidays":[
                  {"greeting":"Test","date":"13-01","icons":["a.svg"]}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationMinutes":60,"holidays":[
                  {"greeting":"Test","date":"12/01","icons":["a.svg"]}]}
                """));
        assertThrows(IllegalArgumentException.class, () -> HolidaySupport.parse("""
                {"celebrationMinutes":60,"holidays":[
                  {"greeting":"Test","start":"12-01","end":"12-02","icons":["a.svg"]}]}
                """));
    }
}
