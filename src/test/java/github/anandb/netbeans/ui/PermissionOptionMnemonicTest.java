package github.anandb.netbeans.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mnemonics must stay unique within a button row: Swing activates only the first
 * button whose mnemonic matches, so two options sharing a letter silently make one
 * of them unreachable from the keyboard.
 */
class PermissionOptionMnemonicTest {

    private static char mnemonic(String name, String kind) {
        return new PermissionOption("id", name, kind).mnemonic();
    }

    private static boolean underlines(String name, String kind) {
        char mnemonic = mnemonic(name, kind);
        return mnemonic != 0 && name.toLowerCase(Locale.ROOT).indexOf(Character.toLowerCase(mnemonic)) >= 0;
    }

    @Test
    void allowOnceAndAlwaysAllowDoNotShareAMnemonic() {
        assertEquals('O', mnemonic("Allow Once", "allow_once"));
        assertEquals('A', mnemonic("Always Allow", "allow_always"));
        assertNotEquals(mnemonic("Allow Once", "allow_once"), mnemonic("Always Allow", "allow_always"));
    }

    @Test
    void singleAllowKeepsAltA() {
        assertEquals('A', mnemonic("Allow", "allow_once"));
        assertEquals('A', mnemonic("Allow edit", "allow_once"));
    }

    @Test
    void rejectOnceAndAlwaysRejectDoNotShareAMnemonic() {
        assertEquals('R', mnemonic("Reject Once", "reject_once"));
        assertEquals('W', mnemonic("Reject Always", "reject_always"));
        assertNotEquals(mnemonic("Reject Once", "reject_once"), mnemonic("Reject Always", "reject_always"));
    }

    @Test
    void denyKeepsAltRBecauseAltDBelongsToShowDiff() {
        assertEquals('R', mnemonic("Deny", "reject_once"));
    }

    @Test
    void gooseOptionIdsMapToTheSameLetters() {
        assertEquals('A', mnemonic("allow_always", "allow_always"));
        assertEquals('O', mnemonic("allow_once", "allow_once"));
        assertEquals('R', mnemonic("reject_once", "reject_once"));
        assertEquals('W', mnemonic("reject_always", "reject_always"));
    }

    @Test
    void everyLetterIsUnderlinableInItsLabel() {
        // AbstractButton only underlines a mnemonic it can find in the label text.
        assertTrue(underlines("Always Allow", "allow_always"));
        assertTrue(underlines("Allow Once", "allow_once"));
        assertTrue(underlines("Reject Once", "reject_once"));
        assertTrue(underlines("Reject Always", "reject_always"));
    }

    @Test
    void unknownKindGetsNoMnemonic() {
        assertEquals(0, mnemonic("Whatever", "other"));
        assertEquals(0, mnemonic("Whatever", null));
    }

    private static PermissionOption option(String name, String kind) {
        return new PermissionOption("id", name, kind);
    }

    private static PermissionOption devin(String id, String name, String kind) {
        return new PermissionOption(id, name, kind);
    }

    /** The six options Devin sent for a `git reset` request (wire log, 2026-10-27). */
    private static PermissionOption[] devinRow() {
        return new PermissionOption[] {
            devin("allow_once", "Allow", "allow_once"),
            devin("allow_session", "Yes, allow `git reset` commands (this session)", "allow_always"),
            devin("allow_always", "Yes, always allow `git reset` commands in `bridge`", "allow_always"),
            devin("allow_always_global", "Yes, always allow `git reset` commands in all projects", "allow_always"),
            devin("switch_bypass", "Yes, switch to bypass mode", "allow_always"),
            devin("reject_once", "Reject", "reject_once"),
        };
    }

    @Test
    void devinRowMapsOnceSessionThenProjectAlways() {
        char[] letters = PermissionOption.mnemonics(List.of(devinRow()));
        assertArrayEquals(new char[] {'O', 'S', 'A', 'P', 'B', 'R'}, letters);
    }

    @Test
    void devinLettersComeFromTheOptionIdNotTheKind() {
        // Every allow option arrives as kind "allow_always"; only the id orders them.
        for (PermissionOption option : devinRow()) {
            char expected = switch (option.id()) {
                case "allow_once" -> 'O';
                case "allow_session" -> 'S';
                case "allow_always" -> 'A';
                case "allow_always_global" -> 'P';
                case "switch_bypass" -> 'B';
                default -> 'R';
            };
            assertEquals(expected, option.mnemonic(), option.id());
        }
    }

    @Test
    void devinRowLettersAreUnderlinable() {
        PermissionOption[] options = devinRow();
        char[] letters = PermissionOption.mnemonics(List.of(options));
        for (int i = 0; i < options.length; i++) {
            String label = options[i].name().toLowerCase(Locale.ROOT);
            assertTrue(label.indexOf(Character.toLowerCase(letters[i])) >= 0,
                    options[i].name() + " cannot underline " + letters[i]);
        }
    }

    @Test
    void reorderedRowKeepsEachScopeOnItsDocumentedLetter() {
        // The server may reorder its options; the letters belong to the scopes,
        // not to array positions, so a reordered row must map the same id to the
        // same letter.
        PermissionOption[] options = new PermissionOption[] {
            devin("reject_once", "Reject", "reject_once"),
            devin("switch_bypass", "Yes, switch to bypass mode", "allow_always"),
            devin("allow_once", "Allow", "allow_once"),
            devin("allow_always_global", "Yes, always allow `git reset` commands in all projects", "allow_always"),
            devin("allow_session", "Yes, allow `git reset` commands (this session)", "allow_always"),
            devin("allow_always", "Yes, always allow `git reset` commands in `bridge`", "allow_always"),
        };
        char[] letters = PermissionOption.mnemonics(List.of(options));
        assertEquals('R', letters[0], "reject");
        assertEquals('B', letters[1], "bypass mode");
        assertEquals('O', letters[2], "plain allow");
        assertEquals('P', letters[3], "all projects");
        assertEquals('S', letters[4], "this session");
        assertEquals('A', letters[5], "this project");
    }

    @Test
    void rowLettersAreNeverRepeated() {
        char[] letters = PermissionOption.mnemonics(List.of(devinRow()));
        Set<Character> seen = new HashSet<>();
        for (char letter : letters) {
            assertNotEquals(0, letter);
            assertTrue(seen.add(letter), "duplicate mnemonic " + letter);
        }
    }

    @Test
    void gooseRowKeepsItsDocumentedLetters() {
        char[] letters = PermissionOption.mnemonics(List.of(
                option("Allow Once", "allow_once"),
                option("Always Allow", "allow_always"),
                option("Reject Once", "reject_once")));
        assertArrayEquals(new char[] {'O', 'A', 'R'}, letters);
    }

    @Test
    void loneAllowNextToARejectKeepsAltA() {
        char[] letters = PermissionOption.mnemonics(List.of(
                option("Allow", "allow_once"), option("Deny", "reject_once")));
        assertArrayEquals(new char[] {'A', 'R'}, letters);
    }

    @Test
    void identicalLabelsStillGetDistinctLetters() {
        char[] letters = PermissionOption.mnemonics(List.of(
                option("Allow", "allow_once"), option("Allow", "allow_once")));
        assertArrayEquals(new char[] {'A', 'O'}, letters);
    }
}
