package github.anandb.netbeans.ui.editor;

import java.util.Objects;

/**
 * Minimal stand-in for {@code org.netbeans.libs.git.GitUser}; the git library is
 * absent from the test classpath, but its public accessors are all the hook uses.
 */
public final class FakeGitUser {

    private final String name;
    private final String email;

    public FakeGitUser(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public String getEmailAddress() {
        return email;
    }

    /** Matches {@code GitUser.equals}: the same commit identity is the same author. */
    @Override
    public boolean equals(Object other) {
        return other instanceof FakeGitUser user
                && Objects.equals(name, user.name)
                && Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email);
    }
}
