package github.anandb.netbeans.support;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretPathsTest {

    @Test
    void credentialFilesAndDirectoriesAreSecret(@TempDir File project) {
        for (String path : new String[]{
                ".env", ".env.local", ".netrc", ".npmrc", ".git-credentials",
                "id_rsa", "server.pem", "server.key", "app.p12", "app.jks",
                "credentials", ".ssh/config", ".aws/credentials", ".kube/config"}) {
            assertTrue(SecretPaths.isSecret(new File(project, path).getPath()), path);
        }
    }

    @Test
    void ordinaryFilesAndTemplatesAreNotSecret(@TempDir File project) {
        for (String path : new String[]{
                "src/Main.java", "pom.xml", ".gitignore", "README.md",
                "id_rsa.pub", "server.pem.pub", ".env.example", ".env.sample", ".env.template"}) {
            assertFalse(SecretPaths.isSecret(new File(project, path).getPath()), path);
        }
        assertFalse(SecretPaths.isSecret(null));
    }
}
