package github.anandb.netbeans.manager;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.support.Logger;
import org.apache.commons.lang3.tuple.Pair;

import static org.apache.commons.lang3.StringUtils.abbreviateMiddle;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public final class ToolMetadataExtractor {
    private static final Logger LOG = new Logger(ToolMetadataExtractor.class);
    private static final Pattern[] METADATA_PATTERNS = {
        Pattern.compile("<!--.*?-->", Pattern.DOTALL),
        Pattern.compile("<metadata>.*?</metadata>", Pattern.DOTALL),
    };

    private static final List<Pair<String, Pattern>> TOOL_CONTENT_PATTERNS = List.of(
        Pair.of("skill", Pattern.compile("<skill_content name=\"([^\"]{2,})\"")),
        Pair.of("", Pattern.compile("<path>([^<]{10,})</path>"))
    );

    private ToolMetadataExtractor() {
    }

    public static String stripMetadata(String text) {
        String stripped = text;
        if (isNotBlank(stripped)) {
            for (Pattern pattern : METADATA_PATTERNS) {
                stripped = pattern.matcher(stripped).replaceAll("");
            }
        }

        return stripped;
    }


    public static String extractToolTitle(ProcessedMessage pm, String kind) {
        String tag = pm.messageId();

        int pos = tag.indexOf(':');
        if (pos > 0 && pos < tag.length()) {
            tag = tag.substring(0, pos);
        } else {
            tag = firstNonBlank(kind, "Tool");
        }

        String identifier = "";
        for (var entry : TOOL_CONTENT_PATTERNS) {
            Matcher m = entry.getValue().matcher(pm.rawText());
            if (m.find()) {
                identifier = m.group(1);
                if (isNotBlank(entry.getKey())) {
                    tag = entry.getKey();
                }

                break;
            }
        }

        LOG.info("Title Attributes {0}/{1}/{2}", tag, identifier, pm.rawText());
        tag = tag.length() < 60 ? tag : "Tool";
        String title = tag + " " + abbreviateMiddle(identifier, "...", 60);
        LOG.info("Title {0}", title);
        return title;
    }
}
