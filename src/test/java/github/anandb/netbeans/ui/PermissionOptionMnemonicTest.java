package github.anandb.netbeans.ui;

import java.util.Locale;

import org.junit.jupiter.api.Test;

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
}
