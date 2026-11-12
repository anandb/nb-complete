package github.anandb.netbeans.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import github.anandb.netbeans.model.HarnessCatalog.Activation;
import github.anandb.netbeans.model.HarnessCatalog.SupportTier;

/**
 * Loads {@code harness-catalog.yaml} into the immutable catalog consumed by
 * {@link HarnessCatalog}. Entries list only the fields that differ from the
 * {@link HarnessBuilder} defaults, so the YAML stays a set of deviations.
 */
final class HarnessCatalogLoader {

    private static final String RESOURCE = "harness-catalog.yaml";

    private static final YAMLMapper MAPPER = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private HarnessCatalogLoader() {}

    /** Immutable result of one catalog load. */
    record Catalog(List<Harness> listed, Map<String, Harness> byId,
            Map<String, Harness> byBinaryName, Map<String, Activation> activations,
            Harness unknown) {

        Harness require(String id) {
            Harness harness = byId.get(id);
            if (harness == null) {
                throw new IllegalStateException(RESOURCE + ": missing required harness '" + id + "'");
            }
            return harness;
        }
    }

    static Catalog load() {
        CatalogFile file;
        try (InputStream in = HarnessCatalogLoader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not found on the classpath");
            }
            file = MAPPER.readValue(in, CatalogFile.class);
        } catch (IOException e) {
            throw new IllegalStateException(RESOURCE + ": " + e.getMessage(), e);
        }
        if (file == null || file.harnesses() == null || file.harnesses().isEmpty()) {
            throw new IllegalStateException(RESOURCE + ": no harnesses defined");
        }

        List<Harness> listed = new ArrayList<>();
        Map<String, Harness> byId = new LinkedHashMap<>();
        Map<String, Harness> byBinaryName = new LinkedHashMap<>();
        Map<String, Activation> activations = new LinkedHashMap<>();
        Harness unknown = null;

        for (Entry entry : file.harnesses()) {
            Harness harness = entry.toHarness();
            if (byId.putIfAbsent(harness.id(), harness) != null) {
                throw new IllegalStateException(RESOURCE + ": duplicate harness id '" + harness.id() + "'");
            }
            if (entry.listed() == null || entry.listed()) {
                if (harness.binaryNames().isEmpty()) {
                    throw new IllegalStateException(
                            RESOURCE + ": listed harness '" + harness.id() + "' has no binaryNames");
                }
                listed.add(harness);
                for (String name : harness.binaryNames()) {
                    String key = HarnessCatalog.stripBinaryExtension(name);
                    if (key != null) {
                        byBinaryName.putIfAbsent(key, harness);
                    }
                }
            } else if (unknown == null) {
                unknown = harness;
            }
            if (entry.activation() != null) {
                activations.put(harness.id(), entry.activation().toActivation());
            }
        }

        if (unknown == null) {
            throw new IllegalStateException(RESOURCE + ": the unlisted 'unknown' harness is required");
        }
        return new Catalog(List.copyOf(listed), Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(byBinaryName), Collections.unmodifiableMap(activations), unknown);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private record CatalogFile(List<Entry> harnesses) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    private record Entry(
            String id,
            String displayName,
            String iconBase,
            List<String> binaryNames,
            String launchArgs,
            Boolean requiresMessageQueue,
            Boolean sendsMcpServerConfig,
            Boolean injectsEditorContext,
            Boolean supportsMessageIds,
            Boolean supportsMcpServer,
            Boolean supportsSessionSetMode,
            Boolean supportsSessionSetConfigOption,
            Boolean supportsSessionSetModel,
            Boolean supportsModelSelection,
            Boolean supportsSessionList,
            Boolean supportsAgentList,
            Boolean separateBubblesPerReplayChunk,
            SupportTier supportTier,
            String limitationsKey,
            String unsupportedModelSelectionMessage,
            String unsupportedModelSelectionPlaceholder,
            Install install,
            String prerequisites,
            String docsUrl,
            List<String> windowsInstallSubDirs,
            Boolean acceptsMcpAuthToken,
            Boolean stripsSystemReminders,
            Boolean sendsSystemPromptAsThought,
            Boolean listed,
            ActivationEntry activation) {

        Harness toHarness() {
            requireText(id, "id");
            requireText(displayName, "displayName");
            requireText(iconBase, "iconBase");

            HarnessBuilder builder = Harness.builder(id, displayName, iconBase);
            if (binaryNames != null && !binaryNames.isEmpty()) {
                builder.binaryNames(binaryNames.toArray(String[]::new));
            }
            if (launchArgs != null) builder.launchArgs(launchArgs);
            if (requiresMessageQueue != null) builder.requiresMessageQueue(requiresMessageQueue);
            if (sendsMcpServerConfig != null) builder.sendsMcpServerConfig(sendsMcpServerConfig);
            if (injectsEditorContext != null) builder.injectsEditorContext(injectsEditorContext);
            if (supportsMessageIds != null) builder.supportsMessageIds(supportsMessageIds);
            if (supportsMcpServer != null) builder.supportsMcpServer(supportsMcpServer);
            if (supportsSessionSetMode != null) builder.supportsSessionSetMode(supportsSessionSetMode);
            if (supportsSessionSetConfigOption != null) {
                builder.supportsSessionSetConfigOption(supportsSessionSetConfigOption);
            }
            if (supportsSessionSetModel != null) builder.supportsSessionSetModel(supportsSessionSetModel);
            if (supportsModelSelection != null) builder.supportsModelSelection(supportsModelSelection);
            if (supportsSessionList != null) builder.supportsSessionList(supportsSessionList);
            if (supportsAgentList != null) builder.supportsAgentList(supportsAgentList);
            if (separateBubblesPerReplayChunk != null) {
                builder.separateBubblesPerReplayChunk(separateBubblesPerReplayChunk);
            }
            if (acceptsMcpAuthToken != null) builder.acceptsMcpAuthToken(acceptsMcpAuthToken);
            if (stripsSystemReminders != null) builder.stripsSystemReminders(stripsSystemReminders);
            if (sendsSystemPromptAsThought != null) {
                builder.sendsSystemPromptAsThought(sendsSystemPromptAsThought);
            }

            String limitationKey = nullToEmpty(limitationsKey);
            SupportTier tier = supportTier == null
                    ? (limitationKey.isEmpty() ? SupportTier.FULL : SupportTier.LIMITED)
                    : supportTier;
            if (tier == SupportTier.LIMITED) {
                if (limitationKey.isEmpty()) {
                    throw new IllegalArgumentException(id + ": supportTier LIMITED needs limitationsKey");
                }
                builder.limited(limitationKey);
            } else if (!limitationKey.isEmpty()) {
                throw new IllegalArgumentException(id + ": limitationsKey requires supportTier LIMITED");
            }

            if (unsupportedModelSelectionMessage != null && !unsupportedModelSelectionMessage.isEmpty()) {
                builder.unsupportedModelSelection(unsupportedModelSelectionMessage,
                        nullToEmpty(unsupportedModelSelectionPlaceholder));
            } else if (unsupportedModelSelectionPlaceholder != null
                    && !unsupportedModelSelectionPlaceholder.isEmpty()) {
                throw new IllegalArgumentException(id + ": unsupportedModelSelectionPlaceholder needs a message");
            }

            if (install != null) {
                requireText(install.windows(), id + ".install.windows");
                requireText(install.mac(), id + ".install.mac");
                requireText(install.linux(), id + ".install.linux");
                builder.install(install.windows(), install.mac(), install.linux());
            }
            if (prerequisites != null) builder.prerequisites(prerequisites);
            if (docsUrl != null) builder.docsUrl(docsUrl);
            if (windowsInstallSubDirs != null && !windowsInstallSubDirs.isEmpty()) {
                builder.windowsInstallSubDirs(windowsInstallSubDirs.toArray(String[]::new));
            }
            return builder.build();
        }

        private static void requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("harness-catalog.yaml: " + field + " is required");
            }
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private record Install(String windows, String mac, String linux) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    private record ActivationEntry(String command, String hint) {
        Activation toActivation() {
            return new Activation(command == null ? "" : command, hint == null ? "" : hint);
        }
    }
}
