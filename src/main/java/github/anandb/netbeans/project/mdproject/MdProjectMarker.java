package github.anandb.netbeans.project.mdproject;

import java.io.IOException;

import org.openide.filesystems.FileObject;

/**
 * The {@code .mdproject} marker that turns an ordinary folder into a Markdown project.
 *
 * <p>Written by the New Project wizard, the "Open as Markdown Project" action and
 * {@code MdProjectFactory}, so the filename stays in one place.</p>
 */
public final class MdProjectMarker {

    /** Name of the marker file that identifies a Markdown project folder. */
    public static final String NAME = ".mdproject";

    private MdProjectMarker() {
    }

    /**
     * The marker plus whether this call created it, so a failed conversion can roll its own marker
     * back without touching one a concurrent writer just made.
     */
    public record Created(FileObject file, boolean created) {
    }

    /**
     * Ensures {@code dir} carries the marker and returns it, together with whether this call
     * created it — false after an early return and after a lost race, so the caller never deletes
     * a marker it did not write. An existing marker is left untouched, so converting a folder
     * twice cannot clobber notes the user added to it.
     *
     * <p>The marker needs no attributes: {@link MdProjectInformation} takes the project's name
     * from the directory. Writing one would put a {@code .nbattrs} file in the user's folder.</p>
     *
     * <p>Safe under a lost race: {@link MdProjectFactory#saveProject} runs on a save thread while
     * the wizard or the action's background worker may create the same marker, so a
     * "file already exists" answer returns whatever that other writer created.</p>
     *
     * @return the marker file and whether this call created it
     */
    public static Created ensureCreating(FileObject dir) throws IOException {
        FileObject marker = dir.getFileObject(NAME);
        if (marker != null) {
            return new Created(marker, false);
        }
        try {
            return new Created(dir.createData(NAME), true);
        } catch (IOException ex) {
            FileObject raced = dir.getFileObject(NAME);
            if (raced != null) {
                return new Created(raced, false);
            }
            throw ex;
        }
    }

    /**
     * Ensures {@code dir} carries the marker. An existing marker is left untouched, so converting
     * a folder twice cannot clobber notes the user added to it.
     *
     * @return the marker file
     */
    public static FileObject ensure(FileObject dir) throws IOException {
        return ensureCreating(dir).file();
    }
}
