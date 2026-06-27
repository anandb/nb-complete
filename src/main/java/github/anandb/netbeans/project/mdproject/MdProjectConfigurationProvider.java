package github.anandb.netbeans.project.mdproject;

import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Collections;
import org.netbeans.spi.project.ProjectConfigurationProvider;

/**
 * {@link ProjectConfigurationProvider} that exposes a single empty
 * configuration. Ensures NetBeans does not attempt file-level validation
 * against build or source infrastructure for this project type.
 */
final class MdProjectConfigurationProvider
        implements ProjectConfigurationProvider<MdProjectConfiguration> {

    private static final Collection<MdProjectConfiguration> CONFIGS =
            Collections.singletonList(MdProjectConfiguration.INSTANCE);

    @Override
    public Collection<MdProjectConfiguration> getConfigurations() {
        return CONFIGS;
    }

    @Override
    public MdProjectConfiguration getActiveConfiguration() {
        return MdProjectConfiguration.INSTANCE;
    }

    @Override
    public void setActiveConfiguration(MdProjectConfiguration config) {
        // no-op — single configuration
    }

    @Override
    public boolean hasCustomizer() {
        return false;
    }

    @Override
    public void customize() {
        // no-op
    }

    @Override
    public boolean configurationsAffectAction(String action) {
        return false;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        // no-op — configurations never change
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        // no-op
    }
}
