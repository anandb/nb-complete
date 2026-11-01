package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunCommandPolicyTest {

    @Test
    void emptyListsAllowAnyCommand() {
        assertNull(RunCommandPolicy.rejection("rm -rf build && echo done", "", ""));
    }

    @Test
    void allowlistMatchesExecutableNameAndRejectsOthers() {
        assertNull(RunCommandPolicy.rejection("mvn -q test", "mvn,git", ""));
        assertNull(RunCommandPolicy.rejection("./mvnw test", "mvnw", ""),
                "a path matches by executable name");
        assertTrue(RunCommandPolicy.rejection("rm -rf build", "mvn", "").contains("not allowlisted"));
    }

    @Test
    void restrictedModeRejectsShellOperatorsAndEnvAssignments() {
        assertTrue(RunCommandPolicy.rejection("mvn test && rm -rf /", "mvn", "").contains("Shell operators"));
        assertTrue(RunCommandPolicy.rejection("mvn test | tee log", "mvn", "").contains("Shell operators"));
        assertTrue(RunCommandPolicy.rejection("mvn $(rm -rf /)", "mvn", "").contains("Shell operators"));
        assertTrue(RunCommandPolicy.rejection("FOO=1 mvn test", "mvn", "").contains("Environment assignments"));
    }

    @Test
    void denylistBlocksWithoutAnAllowlistAndWinsOverIt() {
        assertNull(RunCommandPolicy.rejection("git status", "", "rm"));
        assertTrue(RunCommandPolicy.rejection("rm -rf build", "", "rm").contains("denied"));
        assertTrue(RunCommandPolicy.rejection("rm -rf build", "rm,git", "rm").contains("denied"),
                "denied wins when a binary appears in both lists");
        assertTrue(RunCommandPolicy.rejection("git status && rm -rf /", "", "rm").contains("Shell operators"),
                "a denylist alone must not allow chaining around it");
    }

    @Test
    void globPatternsMatchExecutableNames() {
        assertNull(RunCommandPolicy.rejection("mvn test", "mvn*", ""));
        assertNull(RunCommandPolicy.rejection("mvnw test", "mvn*", ""));
        assertNull(RunCommandPolicy.rejection("git status", "g?t", ""));
        assertTrue(RunCommandPolicy.rejection("npm test", "mvn*", "").contains("not allowlisted"));
        assertTrue(RunCommandPolicy.rejection("git status", "", "g?t").contains("denied"));
        assertTrue(RunCommandPolicy.rejection("anything", "", "*").contains("denied"),
                "a deny-all glob denies every command");
    }

    @Test
    void aggregateStatesDescribeTheLists() {
        assertTrue(RunCommandPolicy.allowsAllCommands("", ""));
        assertTrue(RunCommandPolicy.allowsAllCommands("*", ""));
        assertFalse(RunCommandPolicy.allowsAllCommands("mvn", ""));
        assertFalse(RunCommandPolicy.allowsAllCommands("", "rm"));
        assertTrue(RunCommandPolicy.deniesAllCommands("", "*"));
        assertTrue(RunCommandPolicy.deniesAllCommands("mvn", "*"));
        assertFalse(RunCommandPolicy.deniesAllCommands("", "rm"));
    }

    @Test
    void parsingIsCaseInsensitiveAndAcceptsPaths() {
        assertEquals(2, RunCommandPolicy.parse("Maven, ./gradlew").size());
        assertNull(RunCommandPolicy.rejection("MAVEN test", "maven", ""));
    }
}
