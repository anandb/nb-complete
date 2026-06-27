package github.anandb.netbeans.project.mdproject;

import org.netbeans.spi.project.ProjectConfiguration;

/**
 * Empty project configuration for {@link MdProject}. Silent configuration
 * that prevents NetBeans from attempting file validation against build
 * infrastructure (no compilation, no source roots).
 */
final class MdProjectConfiguration implements ProjectConfiguration {

    static final MdProjectConfiguration INSTANCE = new MdProjectConfiguration();

    @Override
    public String getDisplayName() {
        return "default";
    }
}
