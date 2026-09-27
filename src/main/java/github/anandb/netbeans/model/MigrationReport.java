package github.anandb.netbeans.model;

import java.util.List;

/**
 * Outcome of the one-shot NbPreferences → database migration.
 *
 * <p>A migration is never a failure: entries that could not be recovered are
 * counted in {@link #skipped()} and explained in {@link #reasons()}, while
 * everything recoverable is written. The caller renders this; the store never
 * touches the toolkit.</p>
 *
 * @param migrated number of entries written to the database
 * @param skipped  number of entries left behind in preferences
 * @param reasons  one line per skipped entry, oldest first, capped by the
 *                 producer so a large failure set cannot flood memory
 */
public record MigrationReport(int migrated, int skipped, List<String> reasons) {

    /** A run that had nothing to do, or that found nothing to complain about. */
    public static MigrationReport none() {
        return new MigrationReport(0, 0, List.of());
    }

    /** True when at least one entry could not be recovered. */
    public boolean hasSkips() {
        return skipped > 0;
    }

    /** Entries examined, recovered or not. */
    public int total() {
        return migrated + skipped;
    }

    /** The reasons as an immutable list, oldest first. */
    @Override
    public List<String> reasons() {
        return reasons == null ? List.of() : List.copyOf(reasons);
    }
}